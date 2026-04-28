(ns dev
  (:require [clojure.java.io :as io])
  (:import (ch.qos.logback.classic Level)
           (java.io File)
           (org.slf4j LoggerFactory)))

(comment
  (config-set {"exporter.hospital.fhir.export" "false"
               "exporter.practitioner.fhir.export" "false"})

  (doto (Generator. (doto (Generator$GeneratorOptions.)
                      (as-> opt
                        (do (set! (.-population opt) 5)))))
    (.run)))

(comment
  (.printStackTrace (doto (Exception. "top" (Exception. "cause"))
                      (.addSuppressed (Exception. "suppressed" (Exception. "suppressed cause")))))

  (.setLevel (LoggerFactory/getLogger "xtdb.fhir.guardrails")
    Level/TRACE))

(->> (line-seq (io/reader "blocked-generator.log"))
  (keep #(second (re-find #"Done inserting patient (\d+)" %)))
  (map parse-long)
  (sort)
  (clojure.set/difference (set (range 50))))

(re-find (re-matcher #"Done inserting patient (\d+)" "hey - Done inserting patient 15"))
(comment)

