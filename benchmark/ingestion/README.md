# Ingestion throughput: Java JDBC vs Node.js Postgres.js

Two minimal clients that insert rows into XTDB over the Postgres wire protocol.
In every variant, rows are grouped into XTDB transactions of `ROWS_PER_TXN`
rows. The comparison isolates one axis: whether the client **pipelines** the
individual `INSERT`s within each transaction (one round trip for the whole
batch) or **waits for each to be acknowledged** before sending the next (one
round trip per row).

| Client          | `MODE=batched`                                       | `MODE=sequential`                           |
| --------------- | ---------------------------------------------------- | ------------------------------------------- |
| Java / JDBC     | `PreparedStatement.addBatch()` + `executeBatch()`    | loop of `PreparedStatement.executeUpdate()` |
| Node / pg.js    | N queries fired with `.execute()` + `Promise.all`    | `for (...) { await sql\`insert ...\` }`     |

Both `batched` variants still issue N separate single-row `INSERT` statements;
pipelining cuts network round trips but keeps the statement count server-side.
If you instead want to see what one multi-row `INSERT` looks like on the Java
side, set `REWRITE_BATCHED_INSERTS=true` — the pgJDBC driver will rewrite the
batch into a single `INSERT ... VALUES (...), (...), ...`.

## Schema

XTDB infers schema from the insert statement, so there is no `CREATE TABLE`.
The Java client supports two row shapes via `ROW_SHAPE`:

- `patient` (default): inserts into the `patient` table with `_id` (36-char
  string), `resource_type`, `status` and `last_updated` — the same row the CDC
  and Kafka source benchmarks land, so all three benchmarks move the same data.
- `payload`: the original shape — `bench` table with `_id` (UUID) and `payload`
  (TEXT), `_valid_from` left to default (transaction time) and `_valid_to` set
  to *now + 1 minute*.

The Node client only implements the `payload` shape.

After the last commit the Java client polls `COUNT(*)` (across all valid time
for the `payload` shape, whose rows expire) until every submitted row is
visible, and reports submission/drain/end-to-end times. On sync runs the drain
is ~0; with `ASYNC_TX=true` the commit ack does not imply visibility, so the
end-to-end figure is the one comparable with the kafka/CDC source benchmarks.

## Configuration (env vars)

| Variable                   | Default                                              | Notes                         |
| -------------------------- | ---------------------------------------------------- | ----------------------------- |
| `XTDB_HOST`                | `localhost`                                          | In-cluster: `xtdb-service.xtdb-deployment.svc.cluster.local` |
| `XTDB_PORT`                | `5432`                                               |                               |
| `XTDB_DB`                  | `xtdb`                                               |                               |
| `XTDB_USER`                | `xtdb`                                               |                               |
| `MODE`                     | `batched`                                            | `batched` \| `sequential`     |
| `TOTAL_ROWS`               | `500000`                                             |                               |
| `ROWS_PER_TXN`             | `1000`                                               |                               |
| `WARMUP_TXNS`              | `5`                                                  | Discarded from stats          |
| `REWRITE_BATCHED_INSERTS`  | `false`                                              | Java only                     |
| `ROW_SHAPE`                | `patient`                                            | Java only, `patient` \| `payload` |
| `DRAIN_TIMEOUT_SECS`       | `3600`                                               | Java only                     |

The programs print rows/sec, txn/sec, and per-transaction mean/p50/p99 latency.

## Run locally

Port-forward XTDB first (`make con` in `AWS Setup/`), then:

```bash
# Java
cd benchmark/ingestion/java
mvn -q package -DskipTests
java -jar target/bench-jdbc.jar   # honors env vars

# Node
cd benchmark/ingestion/js
npm install
node bench.js
```

## Run in-cluster

From `AWS Setup/`:

```bash
# one-time: terraform apply to create the two ECR repos
make bench-java-push
make bench-js-push

# run benchmarks (repeat with different MODE / sizes)
make bench-java-run MODE=batched
make bench-java-logs

make bench-java-run MODE=sequential
make bench-java-logs

make bench-js-run MODE=batched
make bench-js-logs

make bench-js-run MODE=sequential
make bench-js-logs
```

Each `*-run` replaces the previous Job of the same name. Override any
parameter on the command line, e.g.
`make bench-js-run MODE=sequential TOTAL_ROWS=100000 ROWS_PER_TXN=500`.