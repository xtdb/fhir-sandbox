# XTDB & Kafka Setup Makefile
# Manages namespace resources for XTDB deployment on AWS EKS
# Includes: XTDB infrastructure, legacy batch importer, and Synthea data generator

# Namespace settings
NAMESPACE := xtdb-deployment
SERVICE_ACCOUNT := xtdb-service-account
IAM_ROLE_NAME := xtdb-eks-role
S3_BUCKET := xtdb-bucket

# Application settings (legacy batch importer)
# Fetches ECR URL directly from AWS - works on any PC with AWS credentials
ECR_REPO ?= $(shell aws ecr describe-repositories --repository-names xtdb-fhir --query 'repositories[0].repositoryUri' --output text --region eu-west-1 --no-cli-pager 2>/dev/null || echo "NOT_SET")
IMAGE_TAG ?= latest

# XTDB-AWS settings (core XTDB database image)
XTDB_REPO := /home/egg/src/juxt/xtdb
ECR_XTDB_AWS_REPO ?= $(shell aws ecr describe-repositories --repository-names xtdb-aws --query 'repositories[0].repositoryUri' --output text --region eu-west-1 --no-cli-pager 2>/dev/null || echo "NOT_SET")
XTDB_AWS_TAG ?= fhir-custom

# Generator settings (Synthea data generator service)
# Fetches ECR URL directly from AWS - works on any PC with AWS credentials
ECR_GENERATOR_REPO ?= $(shell aws ecr describe-repositories --repository-names xtdb-fhir-generator --query 'repositories[0].repositoryUri' --output text --region eu-west-1 --no-cli-pager 2>/dev/null || echo "NOT_SET")
GENERATOR_TAG ?= latest

# Chaos Mesh settings
CHAOS_MESH_VERSION := 2.7.2
CHAOS_NAMESPACE    := chaos-mesh
# Kubernetes username allowed to submit chaos experiments.
# Defaults to the current user; override with `make chaos-install CHAOS_USER=...`.
CHAOS_USER ?= $(shell kubectl auth whoami -o jsonpath='{.status.userInfo.username}' 2>/dev/null)

# Current environment (read from file if exists)
CURRENT_ENV := $(shell cat .current-env 2>/dev/null)

.PHONY: help setup teardown login kube nam kafka serv iam xtdb prom con pods bucket \
        pg-dep pg-logs pg-stat pg-teardown pg-con \
        cdc-start cdc-stop cdc-setup cdc-teardown cdc-attach cdc-detach \
        cdc-test-insert cdc-test-query \
        kafka-src-start kafka-src-stop kafka-src-attach kafka-src-detach \
        kafka-src-create-topic kafka-src-delete-topic kafka-src-test-insert kafka-src-test-query \
        app-dep app-teardown app-logs xtdb-build xtdb-push xtdb-setup xtdb-upgrade xtdb-logs \
        gen-build gen-push gen-dep gen-logs gen-stat gen-setup gen-teardown gen-tune cloud-logs \
        pggen-dep pggen-logs pggen-stat pggen-setup pggen-teardown pggen-tune \
        log-dep log-logs log-stat log-teardown \
        chaos-install chaos-teardown chaos-apply chaos-delete chaos-status \
        chaos-pg-apply chaos-pg-delete \
        scale-down scale-up

# Show help/all commands
help:
	@echo "XTDB & Kafka Setup Commands"
	@echo ""
	@echo "  make setup          - Full setup inc. terraform (prompts for dev/prod)"
	@echo "  make teardown       - Delete the namespace (removes all resources)"
	@echo "  make scale-down     - Scale XTDB nodes, generator, and guardrails to zero"
	@echo "  make scale-up       - Scale XTDB nodes to 3, generator and guardrails to 1"
	@echo "  make con            - Port-forward and connect to XTDB via psql"
	@echo "  make pods           - Show pods in the namespace"
	@echo "  make bucket         - Show S3 bucket size (objects and GB)"
	@echo "  make cloud-logs     - Tail CloudWatch logs (all cluster pods)"
	@echo ""
	@echo "Individual steps:"
	@echo "  make login          - AWS login (opens browser)"
	@echo "  make kube           - Update kubeconfig for EKS cluster"
	@echo "  make nam            - Create the Kubernetes namespace"
	@echo "  make kafka          - Install Strimzi Kafka operator and cluster"
	@echo "  make serv           - Create the service account"
	@echo "  make iam            - Create and configure IAM role"
	@echo "  make xtdb           - Install XTDB with Helm"
	@echo "  make prom           - Install Prometheus"
	@echo ""
	@echo "PostgreSQL (single-instance Bitnami chart):"
	@echo "  make pg-dep         - Deploy single-instance PostgreSQL with persistent storage"
	@echo "  make pg-logs        - View PostgreSQL logs"
	@echo "  make pg-stat        - Show PostgreSQL pod status"
	@echo "  make pg-con         - Port-forward and connect to PostgreSQL via psql"
	@echo "  make pg-teardown    - Uninstall PostgreSQL (PVC retained)"
	@echo "  make cdc-start      - Set up the cdc database + attach pg_cdc in XTDB (starts CDC)"
	@echo "  make cdc-stop       - Detach pg_cdc + drop the cdc database (stops CDC)"
	@echo "  make cdc-test-insert - Insert a test Patient row into Postgres core.patient (CDC smoke test)"
	@echo "  make cdc-test-query  - Query XTDB pg_cdc core.patient to confirm it replicated"
	@echo ""
	@echo "Kafka external source (!KafkaConnect into the 'events' table):"
	@echo "  make kafka-src-start - Create the source topic + attach kafka_src in XTDB (starts indexing)"
	@echo "  make kafka-src-stop  - Detach kafka_src + delete the source topic (stops indexing)"
	@echo "  make kafka-src-test-insert - Produce a test JSON event into the source topic"
	@echo "  make kafka-src-test-query  - Query XTDB kafka_src events to confirm it indexed"
	@echo ""
	@echo "Legacy app deployment (batch importer from ECR):"
	@echo "  make app-dep        - Deploy xtdb-fhir batch job via Helm"
	@echo "  make app-logs       - View application logs"
	@echo "  make app-teardown   - Uninstall xtdb-fhir's Helm chart"
	@echo ""
	@echo "XTDB-AWS deployment (core database image):"
	@echo "  make xtdb-build     - Build xtdb-aws Docker image via Gradle"
	@echo "  make xtdb-push      - Build and push xtdb-aws image to ECR"
	@echo "  make xtdb-setup     - Full xtdb-aws deployment (build + push + deploy)"
	@echo "  make xtdb-upgrade   - Helm upgrade an existing xtdb-aws release (no install)"
	@echo "  make xtdb-logs      - Tail logs for a chosen xtdb-statefulset member"
	@echo ""
	@echo "Generator deployment (Synthea data generator):"
	@echo "  make gen-build      - Build xtdb-fhir-generator Docker image"
	@echo "  make gen-push       - Build and push generator image to ECR"
	@echo "  make gen-dep        - Deploy generator as a long-running pod"
	@echo "  make gen-logs       - View generator logs"
	@echo "  make gen-stat       - Show generator pod status"
	@echo "  make gen-setup      - Full generator deployment (build + push + deploy)"
	@echo "  make gen-teardown   - Remove generator deployment"
	@echo "  make gen-tune       - Retune generator load (prompts population + interval; rolls pod)"
	@echo "  make pggen-dep      - Deploy Postgres-target generator (writes to cdc db for CDC)"
	@echo "  make pggen-logs     - View Postgres-target generator logs"
	@echo "  make pggen-stat     - Show Postgres-target generator pod status"
	@echo "  make pggen-setup    - Full Postgres-target deployment (shared build + push + deploy)"
	@echo "  make pggen-teardown - Remove Postgres-target generator deployment"
	@echo "  make pggen-tune     - Retune Postgres-target generator load (prompts; rolls pod)"
	@echo ""
	@echo "Fluent Bit log forwarding (CloudWatch Logs):"
	@echo "  make log-dep        - Deploy fluent-bit DaemonSet to amazon-cloudwatch ns"
	@echo "  make log-logs       - Tail fluent-bit pod logs"
	@echo "  make log-stat       - Show fluent-bit pod status"
	@echo "  make log-teardown   - Remove fluent-bit"
	@echo ""
	@echo "Chaos engineering (opt-in, run after setup):"
	@echo "  make chaos-install  - Install Chaos Mesh via Helm"
	@echo "  make chaos-apply    - Apply XTDB pod-kill experiment (every 4m)"
	@echo "  make chaos-delete   - Remove the XTDB pod-kill experiment"
	@echo "  make chaos-pg-apply - Apply Postgres pod-kill experiment (every 15m)"
	@echo "  make chaos-pg-delete- Remove the Postgres pod-kill experiment"
	@echo "  make chaos-status   - Show Chaos Mesh pods and active experiments"
	@echo "  make chaos-teardown - Uninstall Chaos Mesh entirely"
	@echo ""
	@echo "Ingestion benchmark (Java JDBC vs Node.js Postgres.js):"
	@echo "  make bench-java-build/push/run/logs/teardown"
	@echo "  make bench-js-build/push/run/logs/teardown"
	@echo "  make bench-follower-svc            - Apply ClusterIP Service targeting only xtdb-statefulset-1"
	@echo "  make bench-follower-svc-teardown   - Remove the follower-only Service"
	@echo "  Overrides: MODE=batched|sequential TOTAL_ROWS=... ROWS_PER_TXN=... WARMUP_TXNS=..."
	@echo "             ASYNC_TX=true|false (Java only; explicit 'BEGIN (async=true)' per txn)"
	@echo "             XTDB_HOST=xtdb-service-follower.xtdb-deployment.svc.cluster.local (target follower only)"
	@echo ""
	@echo "Current environment: $(if $(CURRENT_ENV),$(CURRENT_ENV),not set)"

# Full setup - terraform (if needed) + all k8s resources (easier to one this way to guarantee environment is consistent)
setup:
	@echo "Select environment:"
	@echo "  1) dev  - Budget config (t4g.medium, reduced resources)"
	@echo "  2) prod - Performance config (i3.large, full resources)"
	@read -p "Enter choice [1/2]: " choice; \
	if [ "$$choice" = "1" ]; then ENV=dev; \
	elif [ "$$choice" = "2" ]; then ENV=prod; \
	else echo "Invalid choice. Defaulting to dev."; ENV=dev; fi; \
	\
	echo ""; \
	echo "=== Starting setup for $$ENV ==="; \
	echo ""; \
	\
	echo "Step 1: AWS Login..."; \
	aws login; \
	\
	if [ "$(CURRENT_ENV)" = "$$ENV" ]; then \
		echo ""; \
		echo "Terraform: Already in $$ENV mode, skipping..."; \
	else \
		echo ""; \
		echo "Step 2: Applying Terraform ($$ENV)..."; \
		terraform -chdir=terraform init -upgrade; \
		terraform -chdir=terraform apply -var-file="$$ENV.tfvars"; \
		echo "$$ENV" > .current-env; \
	fi; \
	\
	echo ""; \
	read -p "Deploy k8s resources? [y/n]: " deploy; \
	if [ "$$deploy" != "y" ] && [ "$$deploy" != "Y" ]; then \
		echo "Stopping after terraform. Run 'make setup' again to deploy k8s resources."; \
		exit 0; \
	fi; \
	\
	echo ""; \
	echo "Step 3: Updating kubeconfig..."; \
	aws eks --region eu-west-1 update-kubeconfig --name xtdb-cluster || true; \
	\
	echo ""; \
	echo "Step 4: Creating namespace..."; \
	kubectl create namespace $(NAMESPACE) 2>/dev/null || echo "Namespace already exists"; \
	\
	echo ""; \
	echo "Step 5: Installing Kafka..."; \
	helm repo add strimzi https://strimzi.io/charts/ 2>/dev/null || true; \
	helm repo update; \
	helm install strimzi-operator strimzi/strimzi-kafka-operator --namespace $(NAMESPACE) 2>/dev/null || echo "Strimzi operator already installed"; \
	echo "Waiting for operator..."; \
	sleep 10; \
	kubectl apply -f kafka/kafka-cluster-$$ENV.yaml -n $(NAMESPACE); \
	\
	echo ""; \
	echo "Step 5.5: Installing PostgreSQL..."; \
	helm upgrade --install postgresql oci://registry-1.docker.io/bitnamicharts/postgresql \
		--namespace $(NAMESPACE) \
		-f postgres/postgres-values.yaml \
		-f postgres/postgres-values-$$ENV.yaml; \
	\
	echo ""; \
	echo "Step 6: Creating service account..."; \
	kubectl create serviceaccount $(SERVICE_ACCOUNT) --namespace $(NAMESPACE) 2>/dev/null || echo "Service account already exists"; \
	\
	echo ""; \
	echo "Step 7: Configuring IAM..."; \
	aws iam create-role \
		--role-name $(IAM_ROLE_NAME) \
		--assume-role-policy-document file://eks_policy_document.json \
		--description "XTDB EKS Role" \
		--no-cli-pager 2>/dev/null || echo "IAM role already exists"; \
	aws iam attach-role-policy \
		--role-name $(IAM_ROLE_NAME) \
		--policy-arn=arn:aws:iam::380613616955:policy/xtdb-s3-access-policy 2>/dev/null || echo "Policy already attached"; \
	ROLE_ARN=$$(aws iam get-role --role-name $(IAM_ROLE_NAME) --query Role.Arn --output text); \
	kubectl annotate serviceaccount $(SERVICE_ACCOUNT) \
		--namespace $(NAMESPACE) \
		eks.amazonaws.com/role-arn=$$ROLE_ARN \
		--overwrite; \
	\
	echo ""; \
	echo "Step 8: Installing XTDB..."; \
	helm upgrade --install xtdb-aws ./helm \
		--namespace $(NAMESPACE) \
		-f helm/values.yaml \
		-f helm/values-$$ENV.yaml \
		--set xtdbConfig.serviceAccount="$(SERVICE_ACCOUNT)" \
		--set xtdbConfig.s3Bucket="$(S3_BUCKET)"; \
	\
	echo ""; \
	echo "Step 9: Installing Prometheus..."; \
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true; \
	helm repo update; \
	helm install prometheus prometheus-community/prometheus \
		--namespace $(NAMESPACE) \
		-f prometheus-config.yaml 2>/dev/null || echo "Prometheus already installed"; \
	\
	echo ""; \
	echo "Step 10: Installing Chaos Mesh..."; \
	helm repo add chaos-mesh https://charts.chaos-mesh.org 2>/dev/null || true; \
	helm repo update; \
	helm upgrade --install chaos-mesh chaos-mesh/chaos-mesh \
		--namespace $(CHAOS_NAMESPACE) --create-namespace \
		--version $(CHAOS_MESH_VERSION) \
		--set chaosDaemon.runtime=containerd \
		--set chaosDaemon.socketPath=/run/containerd/containerd.sock \
		--set chaosDaemon.env.LOG_LEVEL=warn; \
	sed 's|CHAOS_USER_PLACEHOLDER|$(CHAOS_USER)|g' chaos/chaos-rbac.yaml | kubectl apply -f -; \
	\
	echo ""; \
	echo "=== Setup complete! ($$ENV) ==="; \
	echo "Use 'make con' to connect to XTDB"; \
	echo "Use 'make pods' to check pod status"; \
	echo ""; \
	echo "Waiting for XTDB pods to start..."; \
	sleep 10; \
	kubectl logs -f -l app=xtdb-statefulset -n $(NAMESPACE)

# Teardown - remove finalizers, empty S3 bucket, and delete the namespace
teardown:
	@echo ""
	@echo "WARNING: This will DELETE ALL resources in namespace $(NAMESPACE)!"
	@echo "         This includes: XTDB, Kafka, PostgreSQL, Prometheus, and ALL data."
	@echo "         The S3 bucket $(S3_BUCKET) will also be emptied."
	@echo ""
	@read -p "Type 'yes' to confirm deletion: " confirm; \
	if [ "$$confirm" = "yes" ] || [ "$$confirm" = "Yes" ] || [ "$$confirm" = "YES" ]; then \
		echo ""; \
		echo "Removing finalizers from ALL services within namespace..."; \
		kubectl get svc -n $(NAMESPACE) -o name | xargs -I {} kubectl patch {} -n $(NAMESPACE) -p '{"metadata":{"finalizers":null}}' --type=merge 2>/dev/null || true; \
		echo "Emptying S3 bucket $(S3_BUCKET)..."; \
		aws s3 rm s3://$(S3_BUCKET) --recursive || true; \
		echo "Deleting namespace $(NAMESPACE)..."; \
		kubectl delete namespace $(NAMESPACE); \
		echo "Namespace deleted. All resources removed."; \
	else \
		echo "Aborted. No changes made."; \
	fi

# Individual steps (for manual use)
login:
	@echo "Opening AWS login in browser..."
	@aws login

kube:
	@echo "Updating kubeconfig for EKS cluster..."
	@aws eks --region eu-west-1 update-kubeconfig --name xtdb-cluster || echo "Kubeconfig may already be up to date"

nam:
	@echo "Creating namespace $(NAMESPACE)..."
	@kubectl create namespace $(NAMESPACE) || echo "Namespace may already exist"

kafka:
	@if [ -z "$(CURRENT_ENV)" ]; then \
		echo "Select environment:"; \
		echo "  1) dev"; \
		echo "  2) prod"; \
		read -p "Enter choice [1/2]: " choice; \
		if [ "$$choice" = "1" ]; then ENV=dev; else ENV=prod; fi; \
	else \
		ENV=$(CURRENT_ENV); \
		echo "Using current environment: $$ENV"; \
	fi; \
	echo "Adding Strimzi Helm repo..."; \
	helm repo add strimzi https://strimzi.io/charts/ 2>/dev/null || true; \
	helm repo update; \
	echo "Installing Strimzi Kafka operator..."; \
	helm install strimzi-operator strimzi/strimzi-kafka-operator --namespace $(NAMESPACE) 2>/dev/null || echo "Strimzi operator already installed"; \
	echo "Waiting for operator..."; \
	sleep 10; \
	echo "Applying Kafka cluster ($$ENV)..."; \
	kubectl apply -f kafka/kafka-cluster-$$ENV.yaml -n $(NAMESPACE)

serv:
	@echo "Creating service account..."
	@kubectl create serviceaccount $(SERVICE_ACCOUNT) --namespace $(NAMESPACE) || echo "Service account may already exist"

iam:
	@echo "Creating IAM role..."
	@aws iam create-role \
		--role-name $(IAM_ROLE_NAME) \
		--assume-role-policy-document file://eks_policy_document.json \
		--description "XTDB EKS Role" \
		--no-cli-pager || echo "IAM role may already exist"
	@echo "Attaching policy to role..."
	@aws iam attach-role-policy \
		--role-name $(IAM_ROLE_NAME) \
		--policy-arn=arn:aws:iam::380613616955:policy/xtdb-s3-access-policy || echo "Policy may already be attached"
	@echo "Annotating service account with IAM role..."
	$(eval ROLE_ARN := $(shell aws iam get-role --role-name $(IAM_ROLE_NAME) --query Role.Arn --output text))
	@kubectl annotate serviceaccount $(SERVICE_ACCOUNT) \
		--namespace $(NAMESPACE) \
		eks.amazonaws.com/role-arn=$(ROLE_ARN) \
		--overwrite

xtdb:
	@if [ -z "$(CURRENT_ENV)" ]; then \
		echo "Select environment:"; \
		echo "  1) dev"; \
		echo "  2) prod"; \
		read -p "Enter choice [1/2]: " choice; \
		if [ "$$choice" = "1" ]; then ENV=dev; else ENV=prod; fi; \
	else \
		ENV=$(CURRENT_ENV); \
		echo "Using current environment: $$ENV"; \
	fi; \
	echo "Installing/upgrading XTDB with Helm ($$ENV)..."; \
	helm upgrade --install xtdb-aws ./helm \
		--namespace $(NAMESPACE) \
		-f helm/values.yaml \
		-f helm/values-$$ENV.yaml \
		--set xtdbConfig.serviceAccount="$(SERVICE_ACCOUNT)" \
		--set xtdbConfig.s3Bucket="$(S3_BUCKET)"

# Upgrade an already-installed xtdb-aws release (no --install)
xtdb-upgrade:
	@if [ -z "$(CURRENT_ENV)" ]; then \
		echo "Select environment:"; \
		echo "  1) dev"; \
		echo "  2) prod"; \
		read -p "Enter choice [1/2]: " choice; \
		if [ "$$choice" = "1" ]; then ENV=dev; else ENV=prod; fi; \
	else \
		ENV=$(CURRENT_ENV); \
		echo "Using current environment: $$ENV"; \
	fi; \
	echo "Upgrading XTDB with Helm ($$ENV)..."; \
	helm upgrade xtdb-aws ./helm \
		--namespace $(NAMESPACE) \
		-f helm/values.yaml \
		-f helm/values-$$ENV.yaml \
		--set xtdbConfig.serviceAccount="$(SERVICE_ACCOUNT)" \
		--set xtdbConfig.s3Bucket="$(S3_BUCKET)"

prom:
	@echo "Adding Prometheus Helm repo..."
	@helm repo add prometheus-community https://prometheus-community.github.io/helm-charts || true
	@helm repo update
	@echo "Installing Prometheus..."
	@helm install prometheus prometheus-community/prometheus \
		--namespace $(NAMESPACE) \
		-f prometheus-config.yaml

# ===========================================
# PostgreSQL (single-instance Bitnami chart)
# ===========================================

# Deploy a single-instance PostgreSQL with persistent storage
pg-dep:
	@if [ -z "$(CURRENT_ENV)" ]; then \
		echo "Select environment:"; \
		echo "  1) dev"; \
		echo "  2) prod"; \
		read -p "Enter choice [1/2]: " choice; \
		if [ "$$choice" = "1" ]; then ENV=dev; else ENV=prod; fi; \
	else \
		ENV=$(CURRENT_ENV); \
		echo "Using current environment: $$ENV"; \
	fi; \
	echo "Installing/upgrading PostgreSQL with Helm ($$ENV)..."; \
	helm upgrade --install postgresql oci://registry-1.docker.io/bitnamicharts/postgresql \
		--namespace $(NAMESPACE) \
		-f postgres/postgres-values.yaml \
		-f postgres/postgres-values-$$ENV.yaml

# View PostgreSQL logs
pg-logs:
	@kubectl logs -f -l app.kubernetes.io/name=postgresql -n $(NAMESPACE)

# Show PostgreSQL pod status
pg-stat:
	@kubectl get pods -l app.kubernetes.io/name=postgresql -n $(NAMESPACE)

# Uninstall PostgreSQL (PVC is retained — delete it manually to wipe data)
pg-teardown:
	@helm uninstall postgresql --namespace $(NAMESPACE) || true

# Connect to PostgreSQL via port-forward (uses local port 5433 to avoid clashing with 'con')
pg-con:
	@echo "Connecting to PostgreSQL via port-forward..."
	@PGPASS=$$(kubectl get secret postgresql --namespace $(NAMESPACE) -o jsonpath='{.data.postgres-password}' | base64 -d); \
	kubectl port-forward svc/postgresql --namespace $(NAMESPACE) 5433:5432 & \
	PF_PID=$$!; \
	sleep 2 && \
	PGPASSWORD="$$PGPASS" psql "host=localhost port=5433 user=postgres"; \
	kill $$PF_PID 2>/dev/null || true

# ===========================================
# PostgreSQL CDC replication (Postgres publication + XTDB ATTACH DATABASE)
# ===========================================
# cdc-start sets up the Postgres-side publication (on the 'cdc' database) and
# attaches the pg_cdc source database in XTDB. cdc-stop reverses both. SQL lives
# in sql/*.sql. The XTDB attach's Kafka topics and S3 prefix are suffixed with a
# generated UUID so detach + re-attach starts cleanly (no reused topics / data).

# $(call pg-run,<command>) — port-forward to in-cluster PostgreSQL on 5433, expose
# the generated superuser password as PGPASSWORD, run <command>, then tear down
# the port-forward and propagate the command's exit status.
define pg-run
PGPASS=$$(kubectl get secret postgresql --namespace $(NAMESPACE) -o jsonpath='{.data.postgres-password}' | base64 -d); \
kubectl port-forward svc/postgresql --namespace $(NAMESPACE) 5433:5432 >/dev/null 2>&1 & \
PF_PID=$$!; \
sleep 2; \
PGPASSWORD="$$PGPASS" $(1); ST=$$?; \
kill $$PF_PID 2>/dev/null || true; \
exit $$ST
endef

# $(call xtdb-run,<command>) — port-forward to XTDB on 5432, run <command>, then
# tear down the port-forward and propagate the command's exit status.
define xtdb-run
kubectl port-forward svc/xtdb-service --namespace $(NAMESPACE) 5432:5432 >/dev/null 2>&1 & \
PF_PID=$$!; \
sleep 2; \
$(1); ST=$$?; \
kill $$PF_PID 2>/dev/null || true; \
exit $$ST
endef

# Create the 'cdc' database (if missing), 'core' schema, placeholder tables, and
# the 'xtdb' publication — all in one psql session (connects to the default
# 'postgres' db, then \connect's into cdc). See sql/cdc-setup.sql.
cdc-setup:
	@echo "Setting up the cdc database (db, schema, tables, publication)..."
	@$(call pg-run,psql "host=localhost port=5433 user=postgres dbname=postgres" -v ON_ERROR_STOP=1 -f sql/cdc-setup.sql)

# Drop the whole 'cdc' database for a clean slate (cascades the core schema,
# tables, and the xtdb publication; drops the replication slot first). See
# sql/cdc-teardown.sql. Run after the XTDB detach.
cdc-teardown:
	@echo "Tearing down the cdc database..."
	@$(call pg-run,psql "host=localhost port=5433 user=postgres dbname=postgres" -v ON_ERROR_STOP=1 -f sql/cdc-teardown.sql)

# Attach pg_cdc in XTDB (topics/prefix suffixed with a fresh UUID)
cdc-attach:
	@UUID=$$(uuidgen 2>/dev/null | tr '[:upper:]' '[:lower:]' || cat /proc/sys/kernel/random/uuid); \
	echo "Attaching pg_cdc (suffix: $$UUID)..."; \
	$(call xtdb-run,sed "s/__UUID__/$$UUID/g" sql/attach-database.sql | psql "host=localhost port=5432 user=xtdb" -v ON_ERROR_STOP=1)

# Detach pg_cdc in XTDB
cdc-detach:
	@echo "Detaching pg_cdc..."
	@$(call xtdb-run,psql "host=localhost port=5432 user=xtdb" -v ON_ERROR_STOP=1 -f sql/detach-database.sql)

# Start CDC: set up the cdc database (+ schema/tables/publication), then XTDB attach
cdc-start: cdc-setup cdc-attach
	@echo "CDC replication started."

# Stop CDC: XTDB detach, then drop the cdc database for a clean slate
cdc-stop: cdc-detach cdc-teardown
	@echo "CDC replication stopped."

# Smoke test: insert a marker Patient row into Postgres core.patient (replicated via CDC)
cdc-test-insert:
	@echo "Inserting a test Patient row into core.patient on the cdc database..."
	@$(call pg-run,psql "host=localhost port=5433 user=postgres dbname=cdc" -v ON_ERROR_STOP=1 -f sql/cdc-test-insert.sql)

# Smoke test: query XTDB's attached pg_cdc to confirm the row replicated
cdc-test-query:
	@echo "Querying core.patient on the XTDB pg_cdc database..."
	@$(call xtdb-run,psql "host=localhost port=5432 user=xtdb dbname=pg_cdc" -v ON_ERROR_STOP=1 -f sql/cdc-test-query.sql)

# ===========================================
# Kafka external source (Kafka topic -> XTDB ATTACH DATABASE via !KafkaConnect)
# ===========================================
# kafka-src-start creates the source Kafka topic (Strimzi KafkaTopic CR) and
# attaches the kafka_src external source in XTDB, which reads JSON messages from
# that topic and indexes them into the 'events' table. kafka-src-stop reverses
# both. The attach reuses the existing 'kafkaCluster' logCluster; only the S3
# storage prefix is suffixed with a generated UUID so detach + re-attach starts
# from a clean object store (topic names are fixed). SQL lives in sql/*.sql.

# Create the source Kafka topic
kafka-src-create-topic:
	@echo "Creating Kafka source topic..."
	@kubectl apply -f kafka/kafka-source-topic.yaml -n $(NAMESPACE)

# Delete the source Kafka topic
kafka-src-delete-topic:
	@echo "Deleting Kafka source topic..."
	@kubectl delete -f kafka/kafka-source-topic.yaml -n $(NAMESPACE) || true

# Attach kafka_src in XTDB (storage prefix suffixed with a fresh UUID)
kafka-src-attach:
	@UUID=$$(uuidgen 2>/dev/null | tr '[:upper:]' '[:lower:]' || cat /proc/sys/kernel/random/uuid); \
	echo "Attaching kafka_src (storage suffix: $$UUID)..."; \
	$(call xtdb-run,sed "s/__UUID__/$$UUID/g" sql/attach-kafka-source.sql | psql "host=localhost port=5432 user=xtdb" -v ON_ERROR_STOP=1)

# Detach kafka_src in XTDB
kafka-src-detach:
	@echo "Detaching kafka_src..."
	@$(call xtdb-run,psql "host=localhost port=5432 user=xtdb" -v ON_ERROR_STOP=1 -f sql/detach-kafka-source.sql)

# Start the Kafka external source: create the source topic, then XTDB attach
kafka-src-start: kafka-src-create-topic kafka-src-attach
	@echo "Kafka external source started."

# Stop the Kafka external source: XTDB detach, then delete the source topic
kafka-src-stop: kafka-src-detach kafka-src-delete-topic
	@echo "Kafka external source stopped."

# Smoke test: produce a marker JSON event into the source topic (indexed via !KafkaConnect)
kafka-src-test-insert:
	@echo "Producing a test event into the Kafka source topic..."
	@POD=$$(kubectl get pod -n $(NAMESPACE) -l strimzi.io/cluster=kafka -o jsonpath='{.items[0].metadata.name}'); \
	MARKER="evt-$$(date +%s)"; \
	echo "{\"_id\":\"$$MARKER\",\"type\":\"smoke-test\",\"source\":\"kafka-src-test-insert\"}" \
	  | kubectl exec -i $$POD -n $(NAMESPACE) -- /opt/kafka/bin/kafka-console-producer.sh \
	      --bootstrap-server localhost:9092 --topic xtdb-source-events; \
	echo "Produced event $$MARKER."

# Smoke test: query XTDB's attached kafka_src to confirm the event indexed
kafka-src-test-query:
	@echo "Querying the events table on the XTDB kafka_src database..."
	@$(call xtdb-run,psql "host=localhost port=5432 user=xtdb dbname=kafka_src" -v ON_ERROR_STOP=1 -f sql/kafka-src-test-query.sql)

# Connect to XTDB via port-forward (internal NLB not accessible from outside VPC)
con:
	@echo "Connecting to XTDB via port-forward..."
	@kubectl port-forward svc/xtdb-service --namespace $(NAMESPACE) 5432:5432 & \
	PF_PID=$$!; \
	sleep 2 && \
	psql "host=localhost port=5432 user=xtdb"; \
	kill $$PF_PID 2>/dev/null || true

# Get the XTDB NLB hostname (for Grafana config)
xtdb-host:
	@kubectl get svc xtdb-service -n $(NAMESPACE) -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
	@echo ""

# Show pods in namespace
pods:
	@kubectl get pods --namespace $(NAMESPACE)

# Show S3 bucket size (object count and size in GB)
bucket:
	@aws s3 ls s3://$(S3_BUCKET) --recursive --summarize | tail -2 | awk '/Total Size:/ {printf "Total Size: %.2f GB\n", $$3/1024/1024/1024} /Total Objects:/ {print}'

# Tail CloudWatch logs (all pods in cluster)
cloud-logs:
	@aws logs tail /aws/containerinsights/xtdb-cluster/application --follow

# ===========================================
# Legacy Application Deploy Targets (batch importer)
# Uses existing image on ECR - no build required
# ===========================================

# Deploy the legacy batch importer application
app-dep:
	@echo "Deploying xtdb-fhir batch job..."
	@helm upgrade --install xtdb-fhir ../xtdb-fhir/helm \
		--namespace $(NAMESPACE) \
		--set image.repository=$(ECR_REPO) \
		--set image.tag=$(IMAGE_TAG) \
		--wait

# View application logs
app-logs:
	@kubectl logs -f -l app.kubernetes.io/name=xtdb-fhir -n $(NAMESPACE)

# Uninstall the application
app-teardown:
	@helm uninstall xtdb-fhir --namespace $(NAMESPACE) || true

# ===========================================
# XTDB-AWS Build & Deploy Targets (core database image)
# ===========================================

# Build the xtdb-aws Docker image
xtdb-build:
	@echo "Building xtdb-aws shadow JAR via Gradle..."
	cd $(XTDB_REPO) && ./gradlew :docker:aws:shadowJar
	@echo "Building xtdb-aws Docker image..."
	cd $(XTDB_REPO) && docker build \
		-f docker/Dockerfile \
		--network=host \
		--build-arg VARIANT=aws \
		--build-arg GIT_SHA=$(XTDB_AWS_TAG) \
		--build-arg XTDB_VERSION=2.x-SNAPSHOT \
		-t xtdb-aws:$(XTDB_AWS_TAG) \
		.

# Push the xtdb-aws Docker image to ECR
xtdb-push: xtdb-build
	@echo "Pushing xtdb-aws to ECR: $(ECR_XTDB_AWS_REPO):$(XTDB_AWS_TAG)"
	aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin $(ECR_XTDB_AWS_REPO)
	docker tag xtdb-aws:$(XTDB_AWS_TAG) $(ECR_XTDB_AWS_REPO):$(XTDB_AWS_TAG)
	docker push $(ECR_XTDB_AWS_REPO):$(XTDB_AWS_TAG)

# View logs for a chosen xtdb-statefulset member (prompts for the pod ordinal)
xtdb-logs:
	@read -p "Which xtdb-statefulset member? [0-$$(($(shell kubectl get statefulset xtdb-statefulset -n $(NAMESPACE) -o jsonpath='{.spec.replicas}' 2>/dev/null || echo 1)-1))]: " ordinal; \
	echo "Tailing logs for xtdb-statefulset-$$ordinal..."; \
	kubectl logs -f xtdb-statefulset-$$ordinal -n $(NAMESPACE)

# Full xtdb-aws deployment (build + push + deploy)
xtdb-setup: xtdb-push xtdb
	kubectl rollout restart statefulset xtdb-statefulset -n $(NAMESPACE)
	@echo "XTDB-AWS fully deployed!"

# ===========================================
# Generator Build & Deploy Targets (Synthea data generator)
# ===========================================

# Build the generator Docker image
gen-build:
	@echo "Building xtdb-fhir-generator Docker image..."
	@cd ../xtdb-fhir && docker build -t xtdb-fhir-generator:$(GENERATOR_TAG) .

# Push the generator Docker image to ECR
gen-push: gen-build
	@echo "Pushing generator to ECR: $(ECR_GENERATOR_REPO)"
	@aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin $(ECR_GENERATOR_REPO)
	@docker tag xtdb-fhir-generator:$(GENERATOR_TAG) $(ECR_GENERATOR_REPO):$(GENERATOR_TAG)
	@docker push $(ECR_GENERATOR_REPO):$(GENERATOR_TAG)

# Deploy the generator as a long-running pod
gen-dep:
	@echo "Deploying xtdb-fhir-generator..."
	@sed 's|IMAGE_PLACEHOLDER|$(ECR_GENERATOR_REPO):$(GENERATOR_TAG)|g' generator-deployment.yaml | kubectl apply -f - -n $(NAMESPACE)
	@kubectl rollout restart deployment/xtdb-fhir-generator -n $(NAMESPACE)

# View generator logs
gen-logs:
	@kubectl logs -f -l app=xtdb-fhir-generator -n $(NAMESPACE)

# Get generator pod status
gen-stat:
	@kubectl get pods -l app=xtdb-fhir-generator -n $(NAMESPACE)

# Full generator deployment (build + push + deploy).
gen-setup: gen-push gen-dep
	@echo "Generator (probably) fully deployed!"
	@sleep 15
	@kubectl logs -f -l app=xtdb-fhir-generator -n $(NAMESPACE)

# Uninstall the generator
gen-teardown:
	@kubectl delete -f generator-deployment.yaml -n $(NAMESPACE) || true

# Retune the XTDB-target generator load (prompts for population + interval).
# Both are read at startup, so this sets env overrides and rolls the pod.
# Interval accepts a duration (30s, 5m, 1h) or a bare number of seconds.
gen-tune:
	@read -p "Patient population per tick: " pop; \
	read -p "Interval between ticks (e.g. 30s, 5m, 1h, or seconds): " iv; \
	case "$$iv" in \
	  *h) secs=$$(( $${iv%h} * 3600 ));; \
	  *m) secs=$$(( $${iv%m} * 60 ));; \
	  *s) secs=$${iv%s} ;; \
	  *)  secs=$$iv ;; \
	esac; \
	echo "Tuning xtdb-fhir-generator: population=$$pop interval=$${secs}s (rolling)..."; \
	kubectl set env deployment/xtdb-fhir-generator -n $(NAMESPACE) \
	  PATIENT_GENERATOR_POPULATION=$$pop PATIENT_GENERATOR_INTERVAL_SECONDS=$$secs

# ---- Postgres-target generator (second instance, same image, target=postgres) ----
# Writes FHIR data into the Postgres 'cdc' db so it replicates into XTDB via CDC.
# Reuses the shared gen-build/gen-push (one image); only the deployment differs.

# Deploy the Postgres-target generator
pggen-dep:
	@echo "Deploying xtdb-fhir-generator-pg..."
	@sed 's|IMAGE_PLACEHOLDER|$(ECR_GENERATOR_REPO):$(GENERATOR_TAG)|g' pg-generator-deployment.yaml | kubectl apply -f - -n $(NAMESPACE)
	@kubectl rollout restart deployment/xtdb-fhir-generator-pg -n $(NAMESPACE)

# View Postgres-target generator logs
pggen-logs:
	@kubectl logs -f -l app=xtdb-fhir-generator-pg -n $(NAMESPACE)

# Show Postgres-target generator pod status
pggen-stat:
	@kubectl get pods -l app=xtdb-fhir-generator-pg -n $(NAMESPACE)

# Full Postgres-target deployment (shared build + push + deploy)
pggen-setup: gen-push pggen-dep
	@echo "Postgres-target generator (probably) fully deployed!"
	@sleep 15
	@kubectl logs -f -l app=xtdb-fhir-generator-pg -n $(NAMESPACE)

# Uninstall the Postgres-target generator
pggen-teardown:
	@kubectl delete -f pg-generator-deployment.yaml -n $(NAMESPACE) || true

# Retune the Postgres-target generator load (prompts for population + interval).
# Both are read at startup, so this sets env overrides and rolls the pod.
# Interval accepts a duration (30s, 5m, 1h) or a bare number of seconds.
pggen-tune:
	@read -p "Patient population per tick: " pop; \
	read -p "Interval between ticks (e.g. 30s, 5m, 1h, or seconds): " iv; \
	case "$$iv" in \
	  *h) secs=$$(( $${iv%h} * 3600 ));; \
	  *m) secs=$$(( $${iv%m} * 60 ));; \
	  *s) secs=$${iv%s} ;; \
	  *)  secs=$$iv ;; \
	esac; \
	echo "Tuning xtdb-fhir-generator-pg: population=$$pop interval=$${secs}s (rolling)..."; \
	kubectl set env deployment/xtdb-fhir-generator-pg -n $(NAMESPACE) \
	  PATIENT_GENERATOR_POPULATION=$$pop PATIENT_GENERATOR_INTERVAL_SECONDS=$$secs

# ===========================================
# Guardrails Deploy (monitoring daemon)
ECR_GUARDRAILS_REPO ?= $(shell aws ecr describe-repositories --repository-names xtdb-fhir-guardrails --query 'repositories[0].repositoryUri' --output text --region eu-west-1 --no-cli-pager 2>/dev/null || echo "NOT_SET")
GUARDRAILS_TAG ?= latest

guardrails-build:
	@echo "Building xtdb-fhir-guardrails Docker image..."
	@cd ../xtdb-fhir-clj && docker build -t xtdb-fhir-guardrails:$(GUARDRAILS_TAG) .

guardrails-push: guardrails-build
	@echo "Pushing guardrails to ECR: $(ECR_GUARDRAILS_REPO)"
	@aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin $(ECR_GUARDRAILS_REPO)
	@docker tag xtdb-fhir-guardrails:$(GUARDRAILS_TAG) $(ECR_GUARDRAILS_REPO):$(GUARDRAILS_TAG)
	@docker push $(ECR_GUARDRAILS_REPO):$(GUARDRAILS_TAG)

guardrails-dep:
	@echo "Deploying guardrails..."
	@sed 's|IMAGE_PLACEHOLDER|$(ECR_GUARDRAILS_REPO):$(GUARDRAILS_TAG)|g' guardrails-deployment.yaml | kubectl apply -f - -n $(NAMESPACE)
	@kubectl rollout restart deployment/xtdb-fhir-guardrails -n $(NAMESPACE)

guardrails-logs:
	@kubectl logs -f -l app=xtdb-fhir-guardrails -n $(NAMESPACE)

guardrails-stat:
	@kubectl get pods -l app=xtdb-fhir-guardrails -n $(NAMESPACE)

guardrails-setup: guardrails-push guardrails-dep
	@echo "Guardrails deployed!"

guardrails-teardown:
	@kubectl delete deployment xtdb-fhir-guardrails -n $(NAMESPACE) || true

# ===========================================
# Fluent Bit log forwarder (CloudWatch Logs)
# Applies the trimmed cwagent-operator-rendered.yaml; cluster name and region
# are sed-injected. IAM role and Pod Identity association are provisioned by
# Terraform — fluent-bit inherits creds via the cloudwatch-agent SA.
LOG_NAMESPACE := amazon-cloudwatch
LOG_CLUSTER := xtdb-cluster
LOG_REGION := eu-west-1

log-dep:
	@echo "Deploying fluent-bit..."
	@sed -e 's|CLUSTER_NAME_PLACEHOLDER|$(LOG_CLUSTER)|g' \
	     -e 's|AWS_REGION_PLACEHOLDER|$(LOG_REGION)|g' \
	     cwagent-operator-rendered.yaml | kubectl apply -f -
	@kubectl rollout restart daemonset/fluent-bit -n $(LOG_NAMESPACE)

log-logs:
	@kubectl logs -f -l k8s-app=fluent-bit -n $(LOG_NAMESPACE)

log-stat:
	@kubectl get pods -l k8s-app=fluent-bit -n $(LOG_NAMESPACE)

log-teardown:
	@sed -e 's|CLUSTER_NAME_PLACEHOLDER|$(LOG_CLUSTER)|g' \
	     -e 's|AWS_REGION_PLACEHOLDER|$(LOG_REGION)|g' \
	     cwagent-operator-rendered.yaml | kubectl delete -f - || true

# Scale XTDB nodes to 3, generator and guardrails to 1
scale-up:
	@echo "Scaling XTDB statefulset to 3..."
	@kubectl scale statefulset xtdb-statefulset --replicas=3 -n $(NAMESPACE)
	@echo "Scaling generator to 1..."
	@kubectl scale deployment xtdb-fhir-generator --replicas=1 -n $(NAMESPACE) || true
	@echo "Scaling guardrails to 1..."
	@kubectl scale deployment xtdb-fhir-guardrails --replicas=1 -n $(NAMESPACE) || true
	@echo "Done. Use 'make pods' to verify."

# Scale XTDB nodes, generator, and guardrails to zero (stops workloads without removing them)
scale-down:
	@echo "Scaling XTDB statefulset to 0..."
	@kubectl scale statefulset xtdb-statefulset --replicas=0 -n $(NAMESPACE)
	@echo "Scaling generator to 0..."
	@kubectl scale deployment xtdb-fhir-generator --replicas=0 -n $(NAMESPACE) || true
	@echo "Scaling guardrails to 0..."
	@kubectl scale deployment xtdb-fhir-guardrails --replicas=0 -n $(NAMESPACE) || true
	@echo "Done. Use 'make pods' to verify."

# ===========================================
# Chaos Mesh (opt-in chaos engineering)
# ===========================================

chaos-install:
	helm repo add chaos-mesh https://charts.chaos-mesh.org 2>/dev/null || true
	helm repo update
	helm upgrade --install chaos-mesh chaos-mesh/chaos-mesh \
	    --namespace $(CHAOS_NAMESPACE) --create-namespace \
	    --version $(CHAOS_MESH_VERSION) \
	    --set chaosDaemon.runtime=containerd \
	    --set chaosDaemon.socketPath=/run/containerd/containerd.sock \
	    --set chaosDaemon.env.LOG_LEVEL=warn
	sed 's|CHAOS_USER_PLACEHOLDER|$(CHAOS_USER)|g' chaos/chaos-rbac.yaml | kubectl apply -f -

chaos-apply:
	kubectl apply -f chaos/xtdb-podchaos.yaml

chaos-delete:
	kubectl delete -f chaos/xtdb-podchaos.yaml 2>/dev/null || true

chaos-pg-apply:
	kubectl apply -f chaos/postgres-podchaos.yaml

chaos-pg-delete:
	kubectl delete -f chaos/postgres-podchaos.yaml 2>/dev/null || true

chaos-status:
	kubectl get pods -n $(CHAOS_NAMESPACE)
	kubectl get podchaos -n $(CHAOS_NAMESPACE) 2>/dev/null || true

chaos-teardown:
	helm uninstall chaos-mesh --namespace $(CHAOS_NAMESPACE) || true
	kubectl delete namespace $(CHAOS_NAMESPACE) 2>/dev/null || true

# ===========================================
# Ingestion benchmarks (Java JDBC vs Node.js Postgres.js)
# Compares client-side batching vs sequential inserts, grouped into
# XTDB transactions of ROWS_PER_TXN rows each.
#
# Override at invocation, e.g.:
#   make bench-java-run MODE=sequential TOTAL_ROWS=200000
# ===========================================

ECR_BENCH_JAVA_REPO ?= $(shell aws ecr describe-repositories --repository-names xtdb-fhir-bench-java --query 'repositories[0].repositoryUri' --output text --region eu-west-1 --no-cli-pager 2>/dev/null || echo "NOT_SET")
ECR_BENCH_JS_REPO   ?= $(shell aws ecr describe-repositories --repository-names xtdb-fhir-bench-js   --query 'repositories[0].repositoryUri' --output text --region eu-west-1 --no-cli-pager 2>/dev/null || echo "NOT_SET")
BENCH_JAVA_TAG      ?= latest
BENCH_JS_TAG        ?= latest

# Benchmark parameters (same defaults on both runners)
MODE                    ?= batched
TOTAL_ROWS              ?= 500000
ROWS_PER_TXN            ?= 1000
WARMUP_TXNS             ?= 5
REWRITE_BATCHED_INSERTS ?= false
# When true, Java bench issues explicit `BEGIN (async=true)` / `COMMIT` per txn
# so XTDB does not wait for Kafka ack on commit (lower commit latency).
ASYNC_TX                ?= false
# Override to point at a specific node, e.g. the follower-only service:
#   make bench-js-run XTDB_HOST=xtdb-service-follower.xtdb-deployment.svc.cluster.local
XTDB_HOST               ?= xtdb-service.xtdb-deployment.svc.cluster.local

.PHONY: bench-java-build bench-java-push bench-java-run bench-java-logs bench-java-teardown \
        bench-js-build   bench-js-push   bench-js-run   bench-js-logs   bench-js-teardown \
        bench-follower-svc bench-follower-svc-teardown

# ---- Follower-only Service (isolates bench traffic to xtdb-statefulset-1) ----
bench-follower-svc:
	@kubectl apply -f xtdb-follower-service.yaml -n $(NAMESPACE)

bench-follower-svc-teardown:
	@kubectl delete -f xtdb-follower-service.yaml -n $(NAMESPACE) --ignore-not-found

# ---- Java JDBC ----
bench-java-build:
	@echo "Building xtdb-fhir-bench-java image..."
	@cd ../benchmark/ingestion/java && docker build -t xtdb-fhir-bench-java:$(BENCH_JAVA_TAG) .

bench-java-push: bench-java-build
	@echo "Pushing xtdb-fhir-bench-java to ECR: $(ECR_BENCH_JAVA_REPO):$(BENCH_JAVA_TAG)"
	@aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin $(ECR_BENCH_JAVA_REPO)
	@docker tag xtdb-fhir-bench-java:$(BENCH_JAVA_TAG) $(ECR_BENCH_JAVA_REPO):$(BENCH_JAVA_TAG)
	@docker push $(ECR_BENCH_JAVA_REPO):$(BENCH_JAVA_TAG)

bench-java-run:
	@echo "Running bench-java Job (mode=$(MODE) total=$(TOTAL_ROWS) batch=$(ROWS_PER_TXN))..."
	@kubectl delete job bench-java -n $(NAMESPACE) --ignore-not-found
	@sed -e 's|IMAGE_PLACEHOLDER|$(ECR_BENCH_JAVA_REPO):$(BENCH_JAVA_TAG)|g' \
	     -e 's|XTDB_HOST_PLACEHOLDER|$(XTDB_HOST)|g' \
	     -e 's|MODE_PLACEHOLDER|$(MODE)|g' \
	     -e 's|TOTAL_ROWS_PLACEHOLDER|$(TOTAL_ROWS)|g' \
	     -e 's|ROWS_PER_TXN_PLACEHOLDER|$(ROWS_PER_TXN)|g' \
	     -e 's|WARMUP_TXNS_PLACEHOLDER|$(WARMUP_TXNS)|g' \
	     -e 's|REWRITE_BATCHED_INSERTS_PLACEHOLDER|$(REWRITE_BATCHED_INSERTS)|g' \
	     -e 's|ASYNC_TX_PLACEHOLDER|$(ASYNC_TX)|g' \
	     benchmark-java-job.yaml | kubectl apply -f - -n $(NAMESPACE)
	@echo "Waiting for pod to start..."
	@until kubectl get pod -l app=bench-java -n $(NAMESPACE) -o name 2>/dev/null | grep -q .; do sleep 1; done
	@kubectl wait --for=condition=Ready pod -l app=bench-java -n $(NAMESPACE) --timeout=120s 2>/dev/null || true
	@kubectl logs -f -l app=bench-java -n $(NAMESPACE)

bench-java-logs:
	@kubectl logs -f -l app=bench-java -n $(NAMESPACE)

bench-java-teardown:
	@kubectl delete job bench-java -n $(NAMESPACE) --ignore-not-found

# ---- Node.js Postgres.js ----
bench-js-build:
	@echo "Building xtdb-fhir-bench-js image..."
	@cd ../benchmark/ingestion/js && docker build -t xtdb-fhir-bench-js:$(BENCH_JS_TAG) .

bench-js-push: bench-js-build
	@echo "Pushing xtdb-fhir-bench-js to ECR: $(ECR_BENCH_JS_REPO):$(BENCH_JS_TAG)"
	@aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin $(ECR_BENCH_JS_REPO)
	@docker tag xtdb-fhir-bench-js:$(BENCH_JS_TAG) $(ECR_BENCH_JS_REPO):$(BENCH_JS_TAG)
	@docker push $(ECR_BENCH_JS_REPO):$(BENCH_JS_TAG)

bench-js-run:
	@echo "Running bench-js Job (mode=$(MODE) total=$(TOTAL_ROWS) batch=$(ROWS_PER_TXN))..."
	@kubectl delete job bench-js -n $(NAMESPACE) --ignore-not-found
	@sed -e 's|IMAGE_PLACEHOLDER|$(ECR_BENCH_JS_REPO):$(BENCH_JS_TAG)|g' \
	     -e 's|XTDB_HOST_PLACEHOLDER|$(XTDB_HOST)|g' \
	     -e 's|MODE_PLACEHOLDER|$(MODE)|g' \
	     -e 's|TOTAL_ROWS_PLACEHOLDER|$(TOTAL_ROWS)|g' \
	     -e 's|ROWS_PER_TXN_PLACEHOLDER|$(ROWS_PER_TXN)|g' \
	     -e 's|WARMUP_TXNS_PLACEHOLDER|$(WARMUP_TXNS)|g' \
	     benchmark-js-job.yaml | kubectl apply -f - -n $(NAMESPACE)
	@echo "Waiting for pod to start..."
	@until kubectl get pod -l app=bench-js -n $(NAMESPACE) -o name 2>/dev/null | grep -q .; do sleep 1; done
	@kubectl wait --for=condition=Ready pod -l app=bench-js -n $(NAMESPACE) --timeout=120s 2>/dev/null || true
	@kubectl logs -f -l app=bench-js -n $(NAMESPACE)

bench-js-logs:
	@kubectl logs -f -l app=bench-js -n $(NAMESPACE)

bench-js-teardown:
	@kubectl delete job bench-js -n $(NAMESPACE) --ignore-not-found
