-- XTDB-side Kafka external-source attach, run against the XTDB pgwire endpoint.
-- Reads JSON messages from the 'xtdb-source-events' Kafka topic via a
-- !KafkaConnect external source and indexes each one into the 'patient' table.
-- The __UUID__ placeholder is substituted with a fresh UUID by the Makefile so
-- the S3 storage prefix is unique per attachment (detach + re-attach starts from
-- a clean object store). Topic names are fixed (no UUID) for now.
ATTACH DATABASE kafka_src WITH $$
log: !Kafka
  cluster: kafkaCluster
  topic: "xtdb-kafkasrc-replicaLog"
storage: !Remote
  objectStore: !S3
    bucket: "xtdb-bucket"
    prefix: "kafka-src-__UUID__"
externalSource: !KafkaConnect
  remote: kafkaCluster
  topic: "xtdb-source-events"
  connectConfig:
    key.converter: org.apache.kafka.connect.storage.StringConverter
    value.converter: org.apache.kafka.connect.json.JsonConverter
    value.converter.schemas.enable: "false"
  indexer: !Docs
    table: patient
$$;
