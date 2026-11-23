variable "aws_region" {
  description = "Same region as the source file bucket, so replication has no transfer charge."
  type        = string
  default     = "us-east-1"
}

variable "aws_profile" {
  description = "CLI profile for the personal account role fileshare-backup-bucket-admin (MFA)."
  type        = string
  default     = "personal-backup-tf"
}

variable "project_name" {
  description = "Project name used in tags."
  type        = string
  default     = "fileshare"
}

variable "bucket_name" {
  description = "Backup bucket name. The personal account role manages only fileshare-backup-* buckets."
  type        = string

  validation {
    condition     = startswith(var.bucket_name, "fileshare-backup-")
    error_message = "bucket_name must start with fileshare-backup-."
  }
}

variable "source_account_id" {
  description = "Bootcamp account that replicates into this bucket. Keep it only in terraform.tfvars."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.source_account_id))
    error_message = "source_account_id must be a 12-digit account ID."
  }
}

variable "replication_role_name" {
  description = "Replication role created by ../dev-data (name_prefix-s3-backup-replication)."
  type        = string
  default     = "fileshare-dev-s3-backup-replication"
}

variable "noncurrent_version_days" {
  description = "Days an overwritten copy is kept. Current versions never expire: the source may be gone for a long time."
  type        = number
  default     = 90
}
