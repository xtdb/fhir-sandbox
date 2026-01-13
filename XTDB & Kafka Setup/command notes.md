# XTDB AWS Setup Commands

## Step 0: Sign in to AWS

**Note: Make sure you've logged into XTDB FHIR AdministratorAccess on the access portal**

```bash
aws login
```

## Step 1: Initialize Terraform

```bash
terraform init
```

```bash
terraform plan
```

```bash
terraform apply
```

## Step 2: Configure kubectl for EKS

```bash
aws eks --region eu-west-1 update-kubeconfig --name xtdb-cluster
```

## Step 3: Create Kubernetes Namespace

```bash
kubectl create namespace xtdb-deployment
```

## Step 4: Install Kafka [OLD]

**Note: THIS DOESN'T WORK ANY MORE, LET DAN KNOW!**

```bash
helm install kafka oci://registry-1.docker.io/bitnamicharts/kafka \
  --namespace xtdb-deployment \
  --set listeners.client.protocol=PLAINTEXT \
  --set listeners.controller.protocol=PLAINTEXT \
  --set controller.resourcesPreset=medium \
  --set global.defaultStorageClass=gp2 \
  --set controller.nodeSelector.node_pool=xtdbpool
```

## Step 4: Install Strimzi Kafka Operator

```bash
helm repo add strimzi https://strimzi.io/charts/
```

```bash
helm install strimzi-operator strimzi/strimzi-kafka-operator --namespace xtdb-deployment
```

**Note: Use the kafka-cluster.yaml file**

```bash
kubectl apply -f kafka-cluster.yaml
```

## Step 5: Create Service Account

```bash
kubectl create serviceaccount xtdb-service-account --namespace xtdb-deployment
```

## Step 6: Configure IAM Role

Create a policy document for the service account:

```bash
aws iam create-role --role-name xtdb-eks-role --assume-role-policy-document file://eks_policy_document.json --description "XTDB EKS Role" --no-cli-pager
```

```bash
aws iam attach-role-policy --role-name xtdb-eks-role --policy-arn=arn:aws:iam::380613616955:policy/xtdb-s3-access-policy
```
**Note: This is the syntax that works for my 'fish' shell**

```bash
set xtdb_eks_role_arn $(aws iam get-role --role-name xtdb-eks-role --query Role.Arn --output text)

kubectl annotate serviceaccount xtdb-service-account --namespace xtdb-deployment eks.amazonaws.com/role-arn=$xtdb_eks_role_arn
```

## Step 7: Install XTDB with Helm

**Note: Use the adjusted values.yaml file**

```bash
helm install xtdb-aws . \
  --namespace xtdb-deployment \
  --set xtdbConfig.serviceAccount="xtdb-service-account" \
  --set xtdbConfig.s3Bucket="xtdb-bucket"
```

## Step 8: Connect to XTDB

**Note: We use clusterIP for the service, not load balancer, so it's not accessible from outside the cluster, thus the port-forwarding**

```bash
kubectl port-forward svc/xtdb-service --namespace xtdb-deployment 5432:5432 & sleep 2; psql "host=localhost port=5432 user=xtdb"; kill %1
```

# XTDB AWS Set Down Commands

## Step 1: Delete the Namespace

```bash
kubectl delete namespace xtdb-deployment
```

## Step 2: Delete the EKS cluster & Node Groups

This has to be done manually in the AWS Console web interface.

First delete the node groups, which are located within the cluster, towards the bottom of the 'compute' section.

Then delete the cluster on the EKS page.

# Step 3: Delete the S3 Bucket

Can also be done manually in the AWS Console.

Delete the bucket on the S3 page.

# Step 4: Delete the IAM Roles & Policies

Go to the IAM page, then Roles, and delete all the roles that have XTDB in their name.

Then go to the Policies, and delete all of them.

# Step 5: Delete the VPC & it's Subnets

Go to the VPC page, and delete the VPC. This will also delete the subnets within it.
