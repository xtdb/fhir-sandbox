# AWS Setup — Makefile Guide

This directory manages the XTDB deployment on AWS EKS (XTDB database, Kafka, the
Synthea data generator, guardrails, log forwarding, chaos testing and ingestion
benchmarks). Everything is driven through the [`Makefile`](./Makefile).

Run `make help` at any time to list the commands.

## Prerequisites

- `aws` CLI (logged in via `make login` / `aws login`)
- `kubectl` with the cluster kubeconfig (`make kube`)
- `helm`
- `terraform` (for `make setup`)
- `psql` (for `make con`)

The current environment (`dev` / `prod`) is stored in `.current-env` after a
`make setup` run; targets that need it read from there, otherwise they prompt.

### If using profile-based AWS auth

The Makefile uses whatever AWS credentials are active — it doesn't pin a profile.
If you authenticate with a named profile (e.g. `dan`), export it so every
`aws …` call (including the `$(shell aws ecr …)` repo lookups, which otherwise
resolve to `NOT_SET`) and `kubectl` (its exec auth runs `aws eks get-token`) pick
it up:

```sh
export AWS_PROFILE=dan
```

Or pass it per-invocation — make forwards command-line vars into recipes and
`$(shell …)`: `make gen-push AWS_PROFILE=dan`.

If `kubectl` still can't authenticate, regenerate the kubeconfig with the profile
baked into its exec block:

```sh
aws eks update-kubeconfig --name xtdb-cluster --region eu-west-1 --profile dan
```

## Common configuration

| Variable          | Default              | Description                                  |
|-------------------|----------------------|----------------------------------------------|
| `NAMESPACE`       | `xtdb-deployment`    | Kubernetes namespace for all resources       |
| `SERVICE_ACCOUNT` | `xtdb-service-account` | Service account used by XTDB pods          |
| `IAM_ROLE_NAME`   | `xtdb-eks-role`      | IAM role bound to the service account        |
| `S3_BUCKET`       | `xtdb-bucket`        | S3 bucket backing the XTDB object store      |
| `XTDB_AWS_TAG`    | `fhir-custom`        | Tag for the `xtdb-aws` image                 |
| `IMAGE_TAG`       | `latest`             | Tag for the legacy batch importer image      |
| `GENERATOR_TAG`   | `latest`             | Tag for the generator image                  |

## Top-level lifecycle

| Command          | Description                                                        |
|------------------|-------------------------------------------------------------------|
| `make setup`     | Full setup inc. terraform (prompts for dev/prod)                  |
| `make teardown`  | Delete the namespace (removes all resources, empties S3 bucket)    |
| `make scale-down`| Scale XTDB nodes, generator, and guardrails to zero               |
| `make scale-up`  | Scale XTDB nodes to 3, generator and guardrails to 1              |
| `make con`       | Port-forward and connect to XTDB via `psql`                       |
| `make pods`      | Show pods in the namespace                                        |
| `make bucket`    | Show S3 bucket size (object count and GB)                         |
| `make cloud-logs`| Tail CloudWatch logs (all cluster pods)                           |

## Individual setup steps

| Command       | Description                                  |
|---------------|----------------------------------------------|
| `make login`  | AWS login (opens browser)                    |
| `make kube`   | Update kubeconfig for the EKS cluster        |
| `make nam`    | Create the Kubernetes namespace              |
| `make kafka`  | Install Strimzi Kafka operator and cluster   |
| `make serv`   | Create the service account                   |
| `make iam`    | Create and configure the IAM role            |
| `make xtdb`   | Install/upgrade XTDB with Helm               |
| `make prom`   | Install Prometheus                           |
| `make xtdb-host` | Print the XTDB NLB hostname (for Grafana) |

## XTDB-AWS (core database image)

| Command            | Description                                                |
|--------------------|------------------------------------------------------------|
| `make xtdb-build`  | Build the `xtdb-aws` Docker image via Gradle               |
| `make xtdb-push`   | Build and push the `xtdb-aws` image to ECR                 |
| `make xtdb-setup`  | Full deployment (build + push + deploy + rollout restart)  |
| `make xtdb-upgrade`| Helm upgrade an existing `xtdb-aws` release (no install)   |
| `make xtdb-logs`   | Tail logs for a chosen `xtdb-statefulset` member           |

## PostgreSQL (single-instance Bitnami chart)

A single-instance PostgreSQL (`architecture: standalone`) with a persistent
`gp2` PVC, deployed from the Bitnami OCI chart. It is installed as part of
`make setup`, and XTDB depends on it.

Config follows the same base + env-override layout as XTDB:

- [`postgres/postgres-values.yaml`](./postgres/postgres-values.yaml) — common base (standalone, `gp2`, node selector)
- [`postgres/postgres-values-dev.yaml`](./postgres/postgres-values-dev.yaml) — 8Gi PVC, reduced resources
- [`postgres/postgres-values-prod.yaml`](./postgres/postgres-values-prod.yaml) — 50Gi PVC, larger resources (still single-instance)

`make pg-dep` picks the env from `.current-env`, otherwise it prompts dev/prod.

Auth uses chart defaults — a random `postgres` superuser password is generated
into the `postgresql` Secret:

```bash
kubectl get secret postgresql -n xtdb-deployment -o jsonpath='{.data.postgres-password}' | base64 -d
```

The `cdc` database is created by `make cdc-start` (see below), not by the chart.
XTDB connects to it as a `!Postgres` remote (see `remotes:` in [`helm/values.yaml`](./helm/values.yaml)):
the `postgresql` Secret password is injected into the XTDB pods as `PGUSER` /
`PGPASSWORD`, which the node config references via `!Env`.

| Command           | Description                                              |
|-------------------|----------------------------------------------------------|
| `make pg-dep`     | Deploy single-instance PostgreSQL (prompts/uses env)     |
| `make pg-logs`    | View PostgreSQL logs                                     |
| `make pg-stat`    | Show PostgreSQL pod status                               |
| `make pg-con`     | Port-forward and connect to PostgreSQL via `psql` (local port 5433) |
| `make pg-teardown`| Uninstall PostgreSQL (the PVC is retained)              |
| `make cdc-start`  | Create the `cdc` database (schema/tables/publication) + attach `pg_cdc` in XTDB (starts CDC) |
| `make cdc-stop`   | Detach `pg_cdc` + drop the `cdc` database (stops CDC) |
| `make cdc-reattach UUID=<uuid>` | Re-attach an existing `pg_cdc` db in XTDB by its UUID (validates `pg-cdc-<uuid>` exists in S3; XTDB-only, no Postgres-side changes) |
| `make cdc-test-insert` | Insert a marker Patient row into Postgres `core.patient` (CDC smoke test) |
| `make cdc-test-query`  | Query XTDB `pg_cdc` `core.patient` to confirm the row replicated |

### CDC replication (`cdc-start` / `cdc-stop`)

CDC has two sides, both driven from `.sql` files under [`sql/`](./sql/):

- **Postgres** (`sql/cdc-setup.sql`, run against the default `postgres` db) —
  creates the `cdc` database if missing, then `\connect`s into it to create the
  `core` schema, the resource-type tables the generator writes to (`core.patient`,
  `core.observation`, … — flat schema, PK named `_id`, which XTDB requires on every
  replicated row), and the `xtdb` publication (`FOR TABLES IN SCHEMA core`).
  Idempotent — safe to re-run. The tables are pre-created here, *before* the attach,
  on purpose: XTDB's initial snapshot only captures tables in the publication at
  attach time, and a table that joins later has its pre-existing rows silently
  dropped ([xtdb/xtdb#5497](https://github.com/xtdb/xtdb/issues/5497)). Pre-creating
  them puts them in the snapshot; the generator's `CREATE TABLE IF NOT EXISTS` then
  no-ops and it only ever *inserts*.
- **XTDB** (`sql/attach-database.sql`) — `ATTACH DATABASE pg_cdc`, with the `cdc`
  remote as the `!Postgres` external source. The Kafka topics
  (`xtdb-pgcdc-sourceLog-*` / `xtdb-pgcdc-replicaLog-*`) and S3 prefix (`pg-cdc-*`)
  are suffixed with a freshly generated UUID (printed on attach), so detach +
  re-attach starts clean without reusing old topics or object-store data.

`make cdc-start` runs the Postgres setup then the attach; `make cdc-stop` runs
the detach then drops the whole `cdc` database (`sql/cdc-teardown.sql` — drops the
replication slot first, then the database) for a clean slate. The granular
targets — `cdc-setup`, `cdc-attach`, `cdc-detach`, `cdc-teardown` — are also
available individually.

Because `cdc-detach` leaves the object-store data in place, an existing `pg_cdc`
can be brought back with `make cdc-reattach UUID=<uuid>` — this is XTDB-only (no
Postgres-side changes) and re-uses the given UUID's Kafka topics / S3 prefix
rather than minting a fresh one. It first checks `pg-cdc-<uuid>` exists in the S3
bucket and aborts if not, so you only re-attach a db whose durable data is still
present. The UUID is the suffix printed at attach time (`pg-cdc-<uuid>`).

To smoke-test the round-trip once CDC is running, `make cdc-test-insert` writes a
marker Patient row into Postgres `core.patient` (its `status` carries a
timestamp), and `make cdc-test-query` reads the latest `core.patient` rows back
from XTDB's attached `pg_cdc` database — the marker should appear there once
replicated.

**Driving CDC with real FHIR data.** With CDC running, deploy the Postgres-target
generator (`make pggen-setup` — see [Generator](#generator-synthea-data-generator)).
It synthesises FHIR resources and upserts them into `cdc`, one flat table per
resource type in `core` (`_id`, `resource_type`, `status`, `patient_id`,
`encounter_id`, `code`, `last_updated`, `created_at` — a handful of promoted
top-level fields, no jsonb), which CDC replicates into XTDB's `pg_cdc` database.
Compare counts between the source and target to watch replication keep up:

```sql
-- Postgres (source), via `make pg-con` then `\c cdc`:
SELECT count(*) FROM core.patient;
-- XTDB (target), against the attached pg_cdc database
-- (as `make cdc-test-query` does — psql … dbname=pg_cdc):
SELECT count(*) FROM core.patient;
```

> Note: the curated tables in `cdc-setup.sql` cover the resource types Synthea
> emits, so a normal run is fully snapshot-safe. If the generator ever writes a
> resource type *not* in that list, its table is created after attach and is
> subject to [#5497](https://github.com/xtdb/xtdb/issues/5497) (early rows may be
> dropped). Add it to `cdc-setup.sql` and `make cdc-stop && make cdc-start` to
> re-snapshot, or to recover an existing dataset re-attach captures everything
> currently present.

> Note: the publication name (`xtdb`) must match `publicationName` in
> `sql/attach-database.sql`. The Postgres tables need a replica identity for
> updates/deletes — the generator tables use a primary key (`_id`), which covers it.

## Kafka external source (`kafka-src-start` / `kafka-src-stop`)

| Command                   | Description                                                       |
|---------------------------|------------------------------------------------------------------|
| `make kafka-src-start`    | Create the source Kafka topic + attach `kafka_src` in XTDB (starts indexing) |
| `make kafka-src-stop`     | Detach `kafka_src` + delete the source topic (stops indexing)    |
| `make kafka-src-test-insert` | Produce a marker JSON event into the source topic             |
| `make kafka-src-test-query`  | Query XTDB `kafka_src` `patient` to confirm it indexed        |

This attaches a second external source that reads JSON messages straight from a
Kafka topic (rather than Postgres CDC) and indexes each one into XTDB. Two sides:

- **Kafka** (`kafka/kafka-source-topic.yaml`) — a Strimzi `KafkaTopic` CR
  (`xtdb-source-events`) on the existing `kafka` cluster, provisioned by the topic
  operator. This is the topic you produce into.
- **XTDB** (`sql/attach-kafka-source.sql`) — `ATTACH DATABASE kafka_src` with an
  `externalSource: !KafkaConnect` reading `xtdb-source-events`, converting values
  as schemaless JSON and indexing them into the `patient` table via `!Docs`. It
  reuses the existing `kafkaCluster` logCluster. Only the S3 storage prefix
  (`kafka-src-*`) is suffixed with a freshly generated UUID (printed on attach) so
  detach + re-attach starts from a clean object store; the topic names are fixed.

`make kafka-src-start` creates the topic then runs the attach; `make kafka-src-stop`
runs the detach then deletes the topic. The granular targets — `kafka-src-create-topic`,
`kafka-src-attach`, `kafka-src-detach`, `kafka-src-delete-topic` — are also available.

To smoke-test once the source is running, `make kafka-src-test-insert` produces a
marker patient (`{"_id":"patient-smoke-…"}`, shaped like the generator's output)
into the topic, and
`make kafka-src-test-query` reads the latest `patient` rows back from the attached
`kafka_src` database — the marker should appear there once indexed.

> Note: each produced message must carry an `_id` field (XTDB requires it on every
> indexed row); the `!Docs` indexer maps the rest of the JSON onto the `patient` table.

## Legacy batch importer (xtdb-fhir)

| Command            | Description                            |
|--------------------|----------------------------------------|
| `make app-dep`     | Deploy `xtdb-fhir` batch job via Helm  |
| `make app-logs`    | View application logs                  |
| `make app-teardown`| Uninstall the `xtdb-fhir` Helm chart   |

## Generator (Synthea data generator)

The generator continuously synthesises FHIR data with Synthea and writes it out.
A single image runs against either of two **write targets**, selected per
deployment by the `patient-generator.target` property in its ConfigMap:

- **`xtdb`** (`generator-deployment.yaml`) — writes straight to XTDB via
  `INSERT … RECORDS`.
- **`postgres`** (`pg-generator-deployment.yaml`) — writes into the `cdc`
  Postgres database, which CDC replicates into XTDB. This is how we drive the
  CDC path under load (see [CDC replication](#cdc-replication-cdc-start--cdc-stop)).

Both share `gen-build` / `gen-push` (one image); only the deployment differs.

| Command             | Description                                              |
|---------------------|----------------------------------------------------------|
| `make gen-build`    | Build the `xtdb-fhir-generator` Docker image             |
| `make gen-push`     | Build and push the generator image to ECR                |
| `make gen-dep`      | Deploy the XTDB-target generator                         |
| `make gen-logs`     | View XTDB-target generator logs                          |
| `make gen-stat`     | Show XTDB-target generator pod status                    |
| `make gen-setup`    | Full XTDB-target deployment (build + push + deploy)      |
| `make gen-teardown` | Remove the XTDB-target generator deployment             |
| `make gen-tune`     | Retune XTDB-target generator load (prompts; rolls the pod) |
| `make pggen-dep`    | Deploy the Postgres-target generator (writes to `cdc` for CDC) |
| `make pggen-logs`   | View Postgres-target generator logs                      |
| `make pggen-stat`   | Show Postgres-target generator pod status                |
| `make pggen-setup`  | Full Postgres-target deployment (shared build + push + deploy) |
| `make pggen-teardown` | Remove the Postgres-target generator deployment        |
| `make pggen-tune`   | Retune Postgres-target generator load (prompts; rolls the pod) |

### Tuning generator load

Each generator tick synthesises `population` patients, then waits `interval-seconds`
before the next — so throughput ≈ `population / interval`. Both are read at startup,
so `make gen-tune` / `make pggen-tune` prompt for a population count and an interval
(a duration like `30s`, `5m`, `1h`, or bare seconds), set them as env overrides
(`PATIENT_GENERATOR_POPULATION` / `PATIENT_GENERATOR_INTERVAL_SECONDS`, which Spring's
relaxed binding maps onto the properties), and roll the pod to apply them. The new
pod logs the effective values on startup:

```
xtdb.fhir.PatientGenerator : PatientGenerator config: population=50, interval=2s
```

Cross-check what was applied with
`kubectl set env deployment/xtdb-fhir-generator-pg -n $(NAMESPACE) --list | grep PATIENT_GENERATOR`.

## Guardrails (monitoring daemon)

| Command                  | Description                                  |
|--------------------------|----------------------------------------------|
| `make guardrails-build`  | Build the `xtdb-fhir-guardrails` Docker image|
| `make guardrails-push`   | Build and push the guardrails image to ECR   |
| `make guardrails-dep`    | Deploy guardrails                            |
| `make guardrails-logs`   | View guardrails logs                         |
| `make guardrails-stat`   | Show guardrails pod status                   |
| `make guardrails-setup`  | Full guardrails deployment (push + deploy)   |
| `make guardrails-teardown`| Remove the guardrails deployment            |

## Fluent Bit log forwarding (CloudWatch Logs)

| Command            | Description                                          |
|--------------------|------------------------------------------------------|
| `make log-dep`     | Deploy fluent-bit DaemonSet to `amazon-cloudwatch`   |
| `make log-logs`    | Tail fluent-bit pod logs                             |
| `make log-stat`    | Show fluent-bit pod status                           |
| `make log-teardown`| Remove fluent-bit                                    |

## Chaos engineering (opt-in)

| Command              | Description                              |
|----------------------|------------------------------------------|
| `make chaos-install` | Install Chaos Mesh via Helm              |
| `make chaos-apply`   | Apply the XTDB pod-kill experiment (every 4m)       |
| `make chaos-delete`  | Remove the XTDB pod-kill experiment      |
| `make chaos-pg-apply`| Apply the Postgres pod-kill experiment (every 15m)  |
| `make chaos-pg-delete`| Remove the Postgres pod-kill experiment |
| `make chaos-status`  | Show Chaos Mesh pods and active experiments |
| `make chaos-teardown`| Uninstall Chaos Mesh entirely            |

## Ingestion benchmarks (Java JDBC vs Node.js Postgres.js)

Compares client-side batching vs sequential inserts, grouped into XTDB
transactions of `ROWS_PER_TXN` rows each.

| Command                          | Description                                            |
|----------------------------------|--------------------------------------------------------|
| `make bench-java-build`          | Build the Java JDBC benchmark image                    |
| `make bench-java-push`           | Build and push the Java benchmark image to ECR         |
| `make bench-java-run`            | Run the Java benchmark Job and tail logs               |
| `make bench-java-logs`           | Tail Java benchmark logs                               |
| `make bench-java-teardown`       | Delete the Java benchmark Job                          |
| `make bench-js-build`            | Build the Node.js Postgres.js benchmark image          |
| `make bench-js-push`             | Build and push the JS benchmark image to ECR           |
| `make bench-js-run`              | Run the JS benchmark Job and tail logs                 |
| `make bench-js-logs`             | Tail JS benchmark logs                                 |
| `make bench-js-teardown`         | Delete the JS benchmark Job                            |
| `make bench-follower-svc`        | Apply ClusterIP Service targeting only `xtdb-statefulset-1` |
| `make bench-follower-svc-teardown`| Remove the follower-only Service                      |

Overrides (set on the command line):

| Variable                  | Default | Description                                              |
|---------------------------|---------|----------------------------------------------------------|
| `MODE`                    | `batched` | `batched` or `sequential` insert mode                  |
| `TOTAL_ROWS`              | `500000`| Total rows to insert                                     |
| `ROWS_PER_TXN`            | `1000`  | Rows per XTDB transaction                                |
| `WARMUP_TXNS`             | `5`     | Warm-up transactions before measuring                   |
| `REWRITE_BATCHED_INSERTS` | `false` | JDBC `reWriteBatchedInserts` flag                       |
| `ASYNC_TX`                | `false` | Java only — explicit `BEGIN (async=true)` per txn       |
| `XTDB_HOST`               | `xtdb-service.xtdb-deployment.svc.cluster.local` | Target host (e.g. the follower-only service) |

Example:

```bash
make bench-java-run MODE=sequential TOTAL_ROWS=200000
make bench-js-run XTDB_HOST=xtdb-service-follower.xtdb-deployment.svc.cluster.local
```

---

For the manual, step-by-step setup/teardown walkthrough (terraform, IAM, etc.),
see [`command notes.md`](./command%20notes.md).
