# IAM role granting fluent-bit permission to write to CloudWatch Logs.
# Used via EKS Pod Identity (bound to the cloudwatch-agent SA below).
resource "aws_iam_role" "cloudwatch_observability" {
  name = "AmazonEKSCloudWatchObservabilityRole-${module.xtdb_eks.cluster_name}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "pods.eks.amazonaws.com" }
      Action    = ["sts:AssumeRole", "sts:TagSession"]
    }]
  })

  tags = {
    terraform  = "true"
    managed_by = "XTDB Terraform"
  }
}

resource "aws_iam_role_policy_attachment" "cloudwatch_observability" {
  role       = aws_iam_role.cloudwatch_observability.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

# Fluent Bit itself is deployed out-of-band via `make log-dep`, which applies
# `AWS Setup/cwagent-operator-rendered.yaml` (trimmed to fluent-bit only).
# Terraform only owns the AWS-side wiring: the IAM role above and this Pod
# Identity association, which binds `CloudWatchAgentServerPolicy` to the
# `cloudwatch-agent` ServiceAccount fluent-bit runs under.
resource "aws_eks_pod_identity_association" "cloudwatch_observability" {
  cluster_name    = module.xtdb_eks.cluster_name
  namespace       = "amazon-cloudwatch"
  service_account = "cloudwatch-agent"
  role_arn        = aws_iam_role.cloudwatch_observability.arn
}
