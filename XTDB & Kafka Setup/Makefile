# XTDB & Kafka Setup Makefile
# Manages namespace resources for XTDB deployment on AWS EKS

# Namespace settings
NAMESPACE := xtdb-deployment
SERVICE_ACCOUNT := xtdb-service-account
IAM_ROLE_NAME := xtdb-eks-role
S3_BUCKET := xtdb-bucket

# Application settings
ECR_REPO := $(shell terraform -chdir=terraform output -raw ecr_repository_url 2>/dev/null || echo "NOT_SET")
IMAGE_TAG ?= latest

.PHONY: all setup teardown login kube nam kafka serv iam xtdb prom con pods help \
        ecr docker-build docker-push app-deploy app-teardown app-logs app-status app-full

# Show help/all commands
help:
	@echo "XTDB & Kafka Setup Commands"
	@echo ""
	@echo "  make setup     - Full setup: login, kubeconfig, namespace, kafka, xtdb, prometheus"
	@echo "  make teardown  - Delete the namespace (removes all resources)"
	@echo "  make con       - Port-forward and connect to XTDB via psql"
	@echo "  make pods      - Show pods in the namespace"
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
	@echo "Application deployment:"
	@echo "  make ecr            - Create ECR repository (terraform)"
	@echo "  make docker-build   - Build xtdb-fhir Docker image"
	@echo "  make docker-push    - Build and push image to ECR"
	@echo "  make app-deploy     - Deploy xtdb-fhir via Helm"
	@echo "  make app-logs       - View application logs"
	@echo "  make app-setup      - Full app deployment (build + push + deploy)"
	@echo "  make app-teardown   - Uninstall xtdb-fhir's Helm chart"

# Full setup target
setup: login kube nam kafka serv iam xtdb prom
	@echo ""
	@echo "Setup complete!"
	@echo "Use 'make con' to connect to XTDB"
	@echo "Use 'make pods' to check pod status"

# Teardown - remove finalizers, empty S3 bucket, and delete the namespace
teardown:
	@echo "Removing finalizers from ALL services within namespace (stopping any from getting stuck)..."
	kubectl get svc -n xtdb-deployment -o name | xargs -I {} kubectl patch {} -n xtdb-deployment -p '{"metadata":{"finalizers":null}}' --type=merge || true
	@echo "Emptying S3 bucket $(S3_BUCKET)..."
	aws s3 rm s3://$(S3_BUCKET) --recursive || true
	@echo "Deleting namespace $(NAMESPACE)..."
	kubectl delete namespace $(NAMESPACE)
	@echo "Namespace deleted. All resources removed."

# Step 1: AWS Login (opens browser)
login:
	@echo "Opening AWS login in browser..."
	aws login

# Step 2: Update kubeconfig for EKS (in case this is needed)...
kube:
	@echo "Updating kubeconfig for EKS cluster..."
	aws eks --region eu-west-1 update-kubeconfig --name xtdb-cluster || echo "Kubeconfig may already be up to date"

# Step 3: Create namespace
nam:
	@echo "Creating namespace $(NAMESPACE)..."
	kubectl create namespace $(NAMESPACE) || echo "Namespace may already exist"

# Step 4: Install Strimzi Kafka operator and cluster
kafka:
	@echo "Adding Strimzi Helm repo..."
	helm repo add strimzi https://strimzi.io/charts/ || true
	helm repo update
	@echo "Installing Strimzi Kafka operator..."
	helm install strimzi-operator strimzi/strimzi-kafka-operator --namespace $(NAMESPACE) || echo "Strimzi operator may already be installed"
	@echo "Waiting for operator to be ready..."
	sleep 10
	@echo "Applying Kafka cluster configuration..."
	kubectl apply -f kafka-cluster.yaml -n $(NAMESPACE)

# Step 5: Create service account
serv:
	@echo "Creating service account..."
	kubectl create serviceaccount $(SERVICE_ACCOUNT) --namespace $(NAMESPACE) || echo "Service account may already exist"

# Step 6: Create and configure IAM role
iam:
	@echo "Creating IAM role..."
	aws iam create-role \
		--role-name $(IAM_ROLE_NAME) \
		--assume-role-policy-document file://eks_policy_document.json \
		--description "XTDB EKS Role" \
		--no-cli-pager || echo "IAM role may already exist"
	@echo "Attaching policy to role..."
	aws iam attach-role-policy \
		--role-name $(IAM_ROLE_NAME) \
		--policy-arn=arn:aws:iam::380613616955:policy/xtdb-s3-access-policy || echo "Policy may already be attached"
	@echo "Annotating service account with IAM role..."
	$(eval ROLE_ARN := $(shell aws iam get-role --role-name $(IAM_ROLE_NAME) --query Role.Arn --output text))
	kubectl annotate serviceaccount $(SERVICE_ACCOUNT) \
		--namespace $(NAMESPACE) \
		eks.amazonaws.com/role-arn=$(ROLE_ARN) \
		--overwrite

# Step 7: Install XTDB with Helm
xtdb:
	@echo "Installing XTDB with Helm..."
	helm install xtdb-aws ./helm \
		--namespace $(NAMESPACE) \
		--set xtdbConfig.serviceAccount="$(SERVICE_ACCOUNT)" \
		--set xtdbConfig.s3Bucket="$(S3_BUCKET)"

# Step 8: Install Prometheus
prom:
	@echo "Adding Prometheus Helm repo..."
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts || true
	helm repo update
	@echo "Installing Prometheus..."
	helm install prometheus prometheus-community/prometheus \
		--namespace $(NAMESPACE) \
		-f prometheus-config.yaml

# Connect to XTDB via port-forward and psql
con:
	@echo "Connecting to XTDB..."
	@echo "Press Ctrl+C to disconnect"
	kubectl port-forward svc/xtdb-service --namespace $(NAMESPACE) 5432:5432 & \
	sleep 2 && \
	psql "host=localhost port=5432 user=xtdb"; \
	kill %1 2>/dev/null || true

# Show pods in namespace
pods:
	kubectl get pods --namespace $(NAMESPACE)

# ===========================================
# Application Build & Deploy Targets
# ===========================================

# Create ECR repository via Terraform (Only needs to be run once)
ecr:
	@echo "Creating ECR repository..."
	terraform -chdir=terraform init -upgrade
	terraform -chdir=terraform apply -target=aws_ecr_repository.xtdb_fhir -target=aws_ecr_lifecycle_policy.xtdb_fhir_lifecycle -auto-approve
	@echo "ECR repository created: $$(terraform -chdir=terraform output -raw ecr_repository_url)"

# Build the Docker image
docker-build:
	@echo "Building xtdb-fhir Docker image..."
	cd ../xtdb-fhir && docker build -t xtdb-fhir:$(IMAGE_TAG) .

# Tag and push to ECR
docker-push: docker-build
	@echo "Pushing to ECR: $(ECR_REPO)"
	aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin $(ECR_REPO)
	docker tag xtdb-fhir:$(IMAGE_TAG) $(ECR_REPO):$(IMAGE_TAG)
	docker push $(ECR_REPO):$(IMAGE_TAG)

# Deploy the application via Helm
app-deploy:
	@echo "Deploying xtdb-fhir application..."
	helm upgrade --install xtdb-fhir ../xtdb-fhir/helm \
		--namespace $(NAMESPACE) \
		--set image.repository=$(ECR_REPO) \
		--set image.tag=$(IMAGE_TAG) \
		--wait

# View application logs
app-logs:
	kubectl logs -f -l app.kubernetes.io/name=xtdb-fhir -n $(NAMESPACE)

# Get application pod status
app-status:
	kubectl get pods -l app.kubernetes.io/name=xtdb-fhir -n $(NAMESPACE)

# Full application deployment (build + push + deploy) ECR needs to be ran first. Finally, it will keep you updated on the logs...
app-setup: docker-push app-deploy
	@echo "Application fully deployed!"
	sleep 15
	kubectl logs -f -l app.kubernetes.io/name=xtdb-fhir -n $(NAMESPACE)

# Uninstall the application
app-teardown:
	helm uninstall xtdb-fhir --namespace $(NAMESPACE) || true
