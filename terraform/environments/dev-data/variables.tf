variable "aws_region" {
  description = "AWS region for the development environment."
  type        = string
  default     = "us-east-1"
}

variable "owner" {
  description = "Required Owner tag on every resource. Untagged resources may be removed by the account cleanup policy."
  type        = string

  validation {
    condition     = length(trimspace(var.owner)) > 0
    error_message = "owner must be set; every resource needs the Owner tag."
  }
}

variable "project_name" {
  description = "Project name used for resource naming and tags. Must match the application layer."
  type        = string
  default     = "fileshare"
}

variable "environment" {
  description = "Environment name. Must match the application layer."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones used by this environment."
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "public_subnets" {
  description = "Public subnet CIDR blocks for ALB and NAT gateways."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]
}

variable "private_subnets" {
  description = "Private subnet CIDR blocks for ECS application tasks."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]
}

variable "database_subnets" {
  description = "Isolated subnet CIDR blocks for Aurora and ElastiCache."
  type        = list(string)
  default     = ["10.20.20.0/24", "10.20.21.0/24"]
}

variable "file_bucket_name" {
  description = "Globally unique S3 bucket name for uploaded files."
  type        = string
}

variable "force_destroy_buckets" {
  description = "Whether Terraform can delete a non-empty file bucket. Keep false for the persistent layer."
  type        = bool
  default     = false
}

variable "aurora_database_name" {
  description = "Initial Aurora database name."
  type        = string
  default     = "fileshare"
}

variable "aurora_master_username" {
  description = "Aurora master username."
  type        = string
  default     = "fileshare"
}

variable "aurora_master_password_version" {
  description = "Increase to generate a new Aurora master password and write it to Aurora and SSM (SEC-01)."
  type        = number
  default     = 1
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL engine version. Auto-pause needs 16.3 or later on major version 16. 16.6 is no longer offered in us-east-1 (checked 2026-11-18)."
  type        = string
  default     = "16.15"
}

variable "aurora_instance_count" {
  description = "Number of Aurora instances."
  type        = number
  default     = 1
}

variable "aurora_min_capacity" {
  description = "Aurora Serverless v2 minimum ACUs. 0 pauses compute while idle."
  type        = number
  default     = 0
}

variable "aurora_max_capacity" {
  description = "Aurora Serverless v2 maximum ACUs."
  type        = number
  default     = 2
}

variable "aurora_seconds_until_auto_pause" {
  description = "Idle seconds before Aurora pauses when min capacity is 0."
  type        = number
  default     = 300
}

variable "aurora_deletion_protection" {
  description = "Protects the persistent cluster from terraform destroy. Disable explicitly before intended deletion."
  type        = bool
  default     = true
}

variable "aurora_skip_final_snapshot" {
  description = "Whether to skip the final snapshot when the cluster is deleted."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Additional resource tags. Owner is always overridden by var.owner."
  type        = map(string)
  default     = {}
}

variable "budget_limit_usd" {
  description = "Monthly cost budget in USD."
  type        = number
  default     = 10
}

variable "budget_alert_emails" {
  description = "Email addresses notified by the budget. Keep real addresses only in terraform.tfvars."
  type        = list(string)

  validation {
    condition     = length(var.budget_alert_emails) > 0
    error_message = "At least one budget alert email is required."
  }
}

variable "budget_alert_thresholds" {
  description = "Actual-spend alert thresholds as percentages of the budget."
  type        = list(number)
  default     = [50, 80, 100]
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy through OIDC, in owner/repo format."
  type        = string
}

variable "github_branch" {
  description = "GitHub branch allowed to deploy through OIDC."
  type        = string
  default     = "main"
}

variable "github_oidc_thumbprints" {
  description = "Thumbprints for the GitHub Actions OIDC provider."
  type        = list(string)
  default     = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

variable "budget_filter_by_owner_tag" {
  description = "Count only costs tagged Owner=var.owner. Requires Owner to be an active cost allocation tag; otherwise the budget stays at 0."
  type        = bool
  default     = true
}

variable "terraform_state_bucket_name" {
  description = "Terraform state bucket created by terraform/bootstrap (IAC-01), used by the CI roles (IAC-03)."
  type        = string
}
