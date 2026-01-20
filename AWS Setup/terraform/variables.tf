variable "aws_region" {
  description = "The AWS region for deploying XTDB infrastructure."
  type        = string
  default     = "us-east-1"
}

# S3 Storage
variable "s3_bucket_name" {
  description = "The globally unique name of the S3 bucket for XTDB deployment."
  type        = string
  validation {
    condition     = length(var.s3_bucket_name) >= 0
    error_message = "The S3 bucket name must be set, and globally unique."
  }
}

variable "s3_acl" {
  description = "The ACL for the S3 bucket."
  type        = string
  default     = "private"
}

# VPC
variable "vpc_name" {
  description = "The name of the VPC for XTDB deployment."
  type        = string
  default     = "xtdb-vpc"
}

variable "vpc_cidr" {
  description = "The CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "vpc_availability_zones" {
  description = "List of availability zones to use for the VPC subnets."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "vpc_public_subnets" {
  description = "List of public subnet CIDR blocks."
  type        = list(string)
  default     = ["10.0.4.0/24", "10.0.5.0/24", "10.0.6.0/24"]
}

# EKS Cluster
variable "eks_cluster_name" {
  description = "The name of the EKS cluster."
  type        = string
  default     = "xtdb-cluster"
}

variable "eks_cluster_version" {
  description = "The Kubernetes version for the EKS cluster."
  type        = string
  default     = "1.29"
}

variable "eks_public_access" {
  description = "Whether the EKS cluster endpoint should be publicly accessible."
  type        = bool
  default     = true
}

variable "eks_enable_creator_admin_permissions" {
  description = "Enable admin permissions for the cluster creator."
  type        = bool
  default     = true
}

variable "eks_create_cloudwatch_log_group" {
  description = "Whether to create a CloudWatch log group for the EKS cluster."
  type        = bool
  default     = true
}

# Application Node Pool
# Production defaults (i3.large with NVMe). For dev (t4g.medium ARM), use dev.tfvars

variable "use_local_nvme_storage" {
  description = "Whether to use local NVMe storage (for i3 instances). Dev: set to false for EBS-only instances like t4g."
  type        = bool
  default     = true
}

variable "application_node_pool_machine_type" {
  description = "Instance type for the application node pool. Dev: use t4g.medium (ARM, 4GB) via dev.tfvars"
  type        = string
  default     = "i3.large"
}

variable "application_node_pool_ami_type" {
  description = "AMI type for the node pool. Dev: use AL2023_ARM_64_STANDARD for ARM (t4g) via dev.tfvars"
  type        = string
  default     = "AL2023_x86_64_STANDARD"
}

variable "application_node_pool_min_count" {
  description = "Minimum number of nodes in the application node pool. Dev: use 2 via dev.tfvars"
  type        = number
  default     = 3
}

variable "application_node_pool_max_count" {
  description = "Maximum number of nodes in the application node pool."
  type        = number
  default     = 3
}

variable "application_node_pool_desired_count" {
  description = "Desired number of nodes in the application node pool. Dev: use 2 via dev.tfvars"
  type        = number
  default     = 3
}
