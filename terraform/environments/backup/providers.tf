# BACKUP-01 (ADR-002): personal AWS account, backup copies only.
# Applied locally with the MFA role profile; CI has no access to this account.
# State is local and gitignored: the role may manage only fileshare-backup-*
# buckets, and the only resources here can be re-imported by name.
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
  region  = var.aws_region
  profile = var.aws_profile

  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "terraform"
      Purpose   = "backup"
    }
  }
}
