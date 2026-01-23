(ns xtdb.fhir.guardrails
  (:require [mount.core :as mount :refer [defstate]]
            [next.jdbc :as jdbc]
            [xtdb.api :as xt]
            [clojure.tools.logging :as log])
  (:import (java.util.concurrent Executors ScheduledExecutorService TimeUnit)))

(comment
  (mount/start))

(defstate ^{:on-reload :noop, :dynamic true}
  *xt*
  :start (xt/client {:host "localhost"
                     :port 5432
                     :dbname "xtdb"
                     :user "xtdb"
                     :password "xtdb"}))

(def first-row-val (comp val ffirst))

(defn fetch-query-result [xt q-args extract-result-f]
  (jdbc/with-transaction [tx xt]
    (let [raw-result (apply xt/q tx q-args)]
      {:q-result raw-result
       :result (extract-result-f raw-result)
       :q-args q-args
       :snapshot_token (first-row-val (xt/q tx "SHOW SNAPSHOT_TOKEN"))
       :snapshot_time (first-row-val (xt/q tx "SELECT CURRENT_TIME"))})))

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

(defstate ^{:tag ScheduledExecutorService, :on-reload :noop, :dynamic true}
  *scheduler*
  :start (Executors/newSingleThreadScheduledExecutor)
  :stop (.shutdown *scheduler*))

(defstate guardrails-job
  :start (.scheduleWithFixedDelay *scheduler*
           (fn []
             (log/info "Running guardrails...")
             (check-increasing-patient-count! {})
             (log/info "Guardrails run"))
           0
           1 TimeUnit/MINUTES)
  :stop (.cancel guardrails-job false))

(comment
  (mount/start)
  (mount/stop #'guardrails-job))

(comment
  (clojure.pprint/print-table
    (xt/q *xt*
      "SELECT _id, _valid_from, result
       FROM ops.query_result FOR VALID_TIME ALL
       ORDER BY _valid_from")))
