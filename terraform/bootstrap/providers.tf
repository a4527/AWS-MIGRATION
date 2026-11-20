# Creates the S3 bucket that holds the remote state of dev-data and dev (IAC-01).
# This root module itself keeps a local state: the bucket cannot store the state
# that creates it. If this local state is lost, re-import the single bucket.
terraform {
  required_version = ">= 1.11.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.80, < 6.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "terraform"
      Owner     = var.owner
      Layer     = "bootstrap"
    }
  }
}
