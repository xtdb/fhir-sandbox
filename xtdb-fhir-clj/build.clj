(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def uber-file "target/xtdb-fhir-clj.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis (b/create-basis {:project "deps.edn"})
                  :ns-compile ['xtdb.fhir.guardrails]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis (b/create-basis {:project "deps.edn"})
           :main 'xtdb.fhir.guardrails}))

(comment
  (uber nil))