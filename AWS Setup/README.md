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

| Command           | Description                                              |
|-------------------|----------------------------------------------------------|
| `make pg-dep`     | Deploy single-instance PostgreSQL (prompts/uses env)     |
| `make pg-logs`    | View PostgreSQL logs                                     |
| `make pg-stat`    | Show PostgreSQL pod status                               |
| `make pg-con`     | Port-forward and connect to PostgreSQL via `psql` (local port 5433) |
| `make pg-teardown`| Uninstall PostgreSQL (the PVC is retained)              |

## Legacy batch importer (xtdb-fhir)

| Command            | Description                            |
|--------------------|----------------------------------------|
| `make app-dep`     | Deploy `xtdb-fhir` batch job via Helm  |
| `make app-logs`    | View application logs                  |
| `make app-teardown`| Uninstall the `xtdb-fhir` Helm chart   |

## Generator (Synthea data generator)

| Command            | Description                                       |
|--------------------|---------------------------------------------------|
| `make gen-build`   | Build the `xtdb-fhir-generator` Docker image      |
| `make gen-push`    | Build and push the generator image to ECR         |
| `make gen-dep`     | Deploy the generator as a long-running pod         |
| `make gen-logs`    | View generator logs                               |
| `make gen-stat`    | Show generator pod status                         |
| `make gen-setup`   | Full generator deployment (build + push + deploy) |
| `make gen-teardown`| Remove the generator deployment                   |

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
| `make chaos-apply`   | Apply the XTDB pod-kill experiment       |
| `make chaos-delete`  | Remove the pod-kill experiment           |
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
