# ===========================================
# ECR Repositories for ingestion benchmarks
# (Java JDBC client, Node.js Postgres.js client)
# ===========================================

resource "aws_ecr_repository" "xtdb_fhir_bench_java" {
  name                 = "xtdb-fhir-bench-java"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name        = "xtdb-fhir-bench-java"
    Environment = "production"
    Project     = "fhir-sandbox"
  }
}

resource "aws_ecr_lifecycle_policy" "xtdb_fhir_bench_java_lifecycle" {
  repository = aws_ecr_repository.xtdb_fhir_bench_java.name

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

resource "aws_ecr_repository" "xtdb_fhir_bench_js" {
  name                 = "xtdb-fhir-bench-js"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name        = "xtdb-fhir-bench-js"
    Environment = "production"
    Project     = "fhir-sandbox"
  }
}

resource "aws_ecr_lifecycle_policy" "xtdb_fhir_bench_js_lifecycle" {
  repository = aws_ecr_repository.xtdb_fhir_bench_js.name

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

output "ecr_bench_java_repository_url" {
  description = "ECR repository URL for xtdb-fhir-bench-java"
  value       = aws_ecr_repository.xtdb_fhir_bench_java.repository_url
}

output "ecr_bench_js_repository_url" {
  description = "ECR repository URL for xtdb-fhir-bench-js"
  value       = aws_ecr_repository.xtdb_fhir_bench_js.repository_url
}