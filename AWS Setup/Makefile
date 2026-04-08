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

# Generator settings (Synthea data generator service)
# Fetches ECR URL directly from AWS - works on any PC with AWS credentials
ECR_GENERATOR_REPO ?= $(shell aws ecr describe-repositories --repository-names xtdb-fhir-generator --query 'repositories[0].repositoryUri' --output text --region eu-west-1 --no-cli-pager 2>/dev/null || echo "NOT_SET")
GENERATOR_TAG ?= latest

# Chaos Mesh settings
CHAOS_MESH_VERSION := 2.7.2
CHAOS_NAMESPACE    := chaos-mesh

# Current environment (read from file if exists)
CURRENT_ENV := $(shell cat .current-env 2>/dev/null)

.PHONY: help setup teardown login kube nam kafka serv iam xtdb prom con pods bucket \
        app-dep app-teardown app-logs gen-build gen-push gen-dep gen-logs \
        gen-stat gen-setup gen-teardown cloud-logs

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
	@echo "Legacy app deployment (batch importer from ECR):"
	@echo "  make app-dep        - Deploy xtdb-fhir batch job via Helm"
	@echo "  make app-logs       - View application logs"
	@echo "  make app-teardown   - Uninstall xtdb-fhir's Helm chart"
	@echo ""
	@echo "Generator deployment (Synthea data generator):"
	@echo "  make gen-build      - Build xtdb-fhir-generator Docker image"
	@echo "  make gen-push       - Build and push generator image to ECR"
	@echo "  make gen-dep        - Deploy generator as a long-running pod"
	@echo "  make gen-logs       - View generator logs"
	@echo "  make gen-stat       - Show generator pod status"
	@echo "  make gen-setup      - Full generator deployment (build + push + deploy)"
	@echo "  make gen-teardown   - Remove generator deployment"
	@echo ""
	@echo "Chaos engineering (opt-in, run after setup):"
	@echo "  make chaos-install  - Install Chaos Mesh via Helm"
	@echo "  make chaos-apply    - Apply XTDB pod-kill experiment"
	@echo "  make chaos-delete   - Remove the pod-kill experiment"
	@echo "  make chaos-status   - Show Chaos Mesh pods and active experiments"
	@echo "  make chaos-teardown - Uninstall Chaos Mesh entirely"
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
	kubectl apply -f chaos/chaos-rbac.yaml; \
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
	@echo "         This includes: XTDB, Kafka, Prometheus, and ALL data."
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

prom:
	@echo "Adding Prometheus Helm repo..."
	@helm repo add prometheus-community https://prometheus-community.github.io/helm-charts || true
	@helm repo update
	@echo "Installing Prometheus..."
	@helm install prometheus prometheus-community/prometheus \
		--namespace $(NAMESPACE) \
		-f prometheus-config.yaml

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
	kubectl apply -f chaos/chaos-rbac.yaml

chaos-apply:
	kubectl apply -f chaos/xtdb-podchaos.yaml

chaos-delete:
	kubectl delete -f chaos/xtdb-podchaos.yaml 2>/dev/null || true

chaos-status:
	kubectl get pods -n $(CHAOS_NAMESPACE)
	kubectl get podchaos -n $(CHAOS_NAMESPACE) 2>/dev/null || true

chaos-teardown:
	helm uninstall chaos-mesh --namespace $(CHAOS_NAMESPACE) || true
	kubectl delete namespace $(CHAOS_NAMESPACE) 2>/dev/null || true
