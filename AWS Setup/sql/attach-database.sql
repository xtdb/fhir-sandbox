-- XTDB-side CDC attach, run against the XTDB pgwire endpoint.
-- The __UUID__ placeholder is substituted with a fresh UUID by the Makefile so
-- the Kafka topics and S3 prefix are unique per attachment (detach + re-attach
-- starts from a clean slate without reusing old topics or object-store data).
ATTACH DATABASE pg_cdc WITH $$
log: !Kafka
  cluster: "kafkaCluster"
  topic: "xtdb-pgcdc-sourceLog-__UUID__"
  replicaTopic: "xtdb-pgcdc-replicaLog-__UUID__"
storage: !Remote
  objectStore: !S3
    bucket: "xtdb-bucket"
    prefix: "pg-cdc-__UUID__"
externalSource: !Postgres
  remote: "cdc"
  publicationName: "xtdb"
  slotName: "xtdb_cdc"
$$;
