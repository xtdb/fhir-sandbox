(ns dev
  (:import (org.mitre.synthea.engine Generator Generator$GeneratorOptions)
           (org.mitre.synthea.helpers Config)))

(defn config-set [m]
  (doseq [[k v] m]
    (Config/set k v)))

(comment
  (config-set {"exporter.hospital.fhir.export" "false"
               "exporter.practitioner.fhir.export" "false"})

  (doto (Generator. (doto (Generator$GeneratorOptions.)
                      (as-> opt
                        (do (set! (.-population opt) 5)))))
    (.run)))