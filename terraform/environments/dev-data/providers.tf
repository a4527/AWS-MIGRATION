terraform {
  required_version = ">= 1.11.0"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      # seconds_until_auto_pause (Aurora scale to zero) needs 5.80 or later.
      version = ">= 5.80, < 6.0"
    }
    random = {
      source = "hashicorp/random"
      # Ephemeral random_password needs 3.7 or later (SEC-01).
      version = ">= 3.7, < 4.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
      Owner       = var.owner
      Layer       = "data"
    }
  }
}
