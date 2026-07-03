# Kafka source ingestion benchmark

Measures how fast XTDB's `!KafkaConnect` external source (`kafka_src`) indexes
patient documents from the `xtdb-source-events` topic into the `patient` table.

One mode, one number:

1. Records the baseline `SELECT COUNT(*) FROM patient` on `kafka_src`.
2. Produces `TOTAL_MSGS` patient JSON docs to the topic flat-out — a canned
   Synthea patient (`src/main/resources/patient-template.json`, ~3.5 KB, the
   same snake-cased shape the generator's Kafka sink emits) with a fresh UUID
   `_id` per message, keyed by `_id`.
3. Polls the count once a second until baseline + `TOTAL_MSGS` rows are
   visible, logging progress every 10s.

It prints produce time, drain time (produce done → all rows visible),
end-to-end time (produce start → all rows visible) and indexed rows/sec.

## Configuration (env vars)

| Variable             | Default                | Notes                                        |
| -------------------- | ---------------------- | -------------------------------------------- |
| `KAFKA_BOOTSTRAP`    | `localhost:9092`       | In-cluster: `kafka-kafka-bootstrap.xtdb-deployment.svc.cluster.local:9092` |
| `TOPIC`              | `xtdb-source-events`   |                                              |
| `TOTAL_MSGS`         | `500000`               |                                              |
| `XTDB_HOST`          | `localhost`            |                                              |
| `XTDB_PORT`          | `5432`                 |                                              |
| `XTDB_DB`            | `kafka_src`            | The attached external-source database        |
| `XTDB_USER`          | `xtdb`                 |                                              |
| `DRAIN_TIMEOUT_SECS` | `3600`                 | Exits 2 if the topic hasn't drained by then  |

## Run in-cluster

From `AWS Setup/`. The `kafka_src` source must be attached
(`make kafka-src-start`). `bench-kafka-run` refuses to start while the
Kafka-target generator is running (stop it with `make kafkagen-teardown`),
since its writes to the same topic would skew the counts.

```bash
# one-time: terraform apply to create the ECR repo
make bench-kafka-push

make bench-kafka-run                      # 500k messages
make bench-kafka-run TOTAL_MSGS=1000000   # or override
make bench-kafka-logs
```
