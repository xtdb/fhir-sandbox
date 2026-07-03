# CDC source ingestion benchmark

Measures how fast XTDB's `!Postgres` external source (`pg_cdc`) replicates rows
from Postgres `core.patient` into XTDB. The Postgres-side counterpart of
`benchmark/kafka-source`.

One mode, one number:

1. Records the baseline `SELECT COUNT(*) FROM core.patient` on `pg_cdc`.
2. Inserts `TOTAL_ROWS` patient rows into Postgres `core.patient` in batches of
   `ROWS_PER_TXN` (one transaction per batch) — the same flat row shape the
   generator's `PostgresColumnWriter` produces, fresh UUID `_id` per row,
   `status = 'bench'` so the rows are identifiable.
3. Polls the count once a second until baseline + `TOTAL_ROWS` rows are
   visible, logging progress every 10s.

It prints insert time, drain time (insert done → all rows visible),
end-to-end time (insert start → all rows visible) and replicated rows/sec.

## Configuration (env vars)

| Variable             | Default          | Notes                                          |
| -------------------- | ---------------- | ---------------------------------------------- |
| `PG_HOST`            | `localhost`      | In-cluster: `postgresql.xtdb-deployment.svc.cluster.local` |
| `PG_PORT`            | `5432`           |                                                |
| `PG_DB`              | `cdc`            | The publication source database                |
| `PG_USER`            | `postgres`       |                                                |
| `PG_PASSWORD`        | (empty)          | In-cluster: from the `postgresql` Secret       |
| `TOTAL_ROWS`         | `500000`         |                                                |
| `ROWS_PER_TXN`       | `1000`           | Batch/transaction size for the inserts         |
| `XTDB_HOST`          | `localhost`      |                                                |
| `XTDB_PORT`          | `5432`           |                                                |
| `XTDB_DB`            | `pg_cdc`         | The attached external-source database          |
| `XTDB_USER`          | `xtdb`           |                                                |
| `DRAIN_TIMEOUT_SECS` | `3600`           | Exits 2 if replication hasn't caught up by then |

## Run in-cluster

From `AWS Setup/`. The `pg_cdc` source must be attached (`make cdc-start`).
`bench-cdc-run` refuses to start while the Postgres-target generator is running
(stop it with `make pggen-teardown`), since its writes to `core.patient` would
skew the counts.

```bash
# one-time: terraform apply to create the ECR repo
make bench-cdc-push

make bench-cdc-run                      # 500k rows, batches of 1000
make bench-cdc-run TOTAL_ROWS=1000000   # or override
make bench-cdc-logs
```
