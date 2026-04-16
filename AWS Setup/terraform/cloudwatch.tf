# Required for CloudWatch Observability EKS add-on (Container Insights)
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

resource "aws_eks_addon" "cloudwatch_observability" {
  cluster_name = module.xtdb_eks.cluster_name
  addon_name   = "amazon-cloudwatch-observability"

  pod_identity_association {
    role_arn        = aws_iam_role.cloudwatch_observability.arn
    service_account = "cloudwatch-agent"
  }

  configuration_values = jsonencode(yamldecode(file("${path.module}/cloudwatch-observability.yaml")))
}
