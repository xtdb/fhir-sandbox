terraform {
  required_version = ">=1.3"

  backend "s3" {
    bucket  = "xtdb-fhir-terraform-state"
    key     = "terraform/state.tfstate"
    region  = "eu-west-1"
    profile = "xtdb"
  }

required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = ">= 3.5.0, < 4.0.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}
