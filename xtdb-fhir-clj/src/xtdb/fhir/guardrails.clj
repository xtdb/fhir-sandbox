(ns xtdb.fhir.guardrails
  (:gen-class)
  (:require [mount.core :as mount :refer [defstate]]
            [next.jdbc :as jdbc]
            [xtdb.api :as xt]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.sql SQLException)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

(comment
  (mount/start))

(defstate ^{:on-reload :noop, :dynamic true}
  *xt*
  :start (doto (HikariDataSource.)
           (.setDataSource (xt/client {:host (or (env :xtdb-host) "localhost")
                                       :port (or (some-> (env :xtdb-port) parse-long) 5432)
                                       :dbname (or (env :xtdb-dbname) "xtdb")
                                       :user (or (env :xtdb-user) "xtdb")
                                       :password (or (env :xtdb-password) "xtdb")}))
           (.setConnectionTimeout 5000)
           (.setMinimumIdle 1))
  :stop (.close *xt*))

(def first-row-val (comp val ffirst))

(defn fetch-query-result [xt q-args extract-result-f]
  (jdbc/with-transaction [tx xt]
    (let [raw-result (apply xt/q tx q-args)]
      {:q-result raw-result
       :result (extract-result-f raw-result)
       :q-args q-args
       :snapshot_token (first-row-val (xt/q tx "SHOW SNAPSHOT_TOKEN"))
       :snapshot_timestamp (first-row-val (xt/q tx "SELECT CURRENT_TIMESTAMP"))})))

(comment
  (fetch-query-result *xt* ["FROM patient SELECT COUNT(*) AS patient_count"] first-row-val))

(defn check-increasing-patient-count! [{:keys [cold-run?]}]
  (let [prev-result-m (xt/q *xt* "FROM ops.query_result WHERE _id = 'patient_count' SELECT result")
        _ (log/trace "Previous result:" prev-result-m)
        prev-result (-> prev-result-m first :result)
        curr-result-m (fetch-query-result *xt* ["FROM patient SELECT COUNT(*) AS patient_count"] first-row-val)
        _ (log/trace "Current result:" curr-result-m)
        curr-result (:result curr-result-m)]
    (when-not cold-run?
      (xt/submit-tx *xt* [[:put-docs :ops/query-result (merge curr-result-m
                                                             {:xt/id "patient_count"})]]))
    (when (and (some? prev-result)
               (not (<= prev-result curr-result)))
      (log/warn "patient_count decreased! from" prev-result "to" curr-result))))

(comment
  (check-increasing-patient-count! {:cold-run? true})
  (check-increasing-patient-count! {}))

(defstate ^{:tag ScheduledExecutorService, :dynamic true}
  *scheduler*
  :start (Executors/newSingleThreadScheduledExecutor)
  :stop (.shutdown *scheduler*))

(defstate guardrails-job
  :start (.scheduleWithFixedDelay *scheduler*
           (fn []
             (try
               (log/info "Running guardrails...")
               (check-increasing-patient-count! {})
               (log/info "Guardrails run")
               (catch Exception e
                 (when-not (and (.isShutdown *scheduler*)
                                (or (instance? InterruptedException e)
                                    (instance? SQLException e)))
                   (log/error e "Error while running guardrails")))))
           0
           1 TimeUnit/MINUTES)
  :stop (.cancel guardrails-job true))

(comment
  (mount/start)
  (mount/stop)
  (mount/stop #'guardrails-job))

(comment
  (clojure.pprint/print-table
    (xt/q *xt*
      "SELECT _id, _valid_from, result
       FROM ops.query_result FOR VALID_TIME ALL
       ORDER BY _valid_from")))

(defn -main [& _args]
  (log/info "Starting...")
  (mount/start)
  (.addShutdownHook (Runtime/getRuntime)
    (Thread. (fn []
               (log/info "Stopping...")
               (mount/stop)
               (log/info "Stopped"))))
  (log/info "Started"))
