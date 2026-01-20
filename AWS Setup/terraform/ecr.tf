# ===========================================
# ECR Repository for xtdb-fhir application
# ===========================================

resource "aws_ecr_repository" "xtdb_fhir" {
  name                 = "xtdb-fhir"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name        = "xtdb-fhir"
    Environment = "production"
    Project     = "fhir-sandbox"
  }
}

# Lifecycle policy to manage image retention
resource "aws_ecr_lifecycle_policy" "xtdb_fhir_lifecycle" {
  repository = aws_ecr_repository.xtdb_fhir.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 10 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# Output the repository URL for use in CI/CD
output "ecr_repository_url" {
  description = "ECR repository URL for xtdb-fhir"
  value       = aws_ecr_repository.xtdb_fhir.repository_url
}

output "ecr_repository_arn" {
  description = "ECR repository ARN for xtdb-fhir"
  value       = aws_ecr_repository.xtdb_fhir.arn
}
