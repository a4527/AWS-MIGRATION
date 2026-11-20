variable "aws_region" {
  description = "AWS region for the state bucket. Must match the region in each layer's backend.tf."
  type        = string
  default     = "us-east-1"
}

variable "owner" {
  description = "Required Owner tag. Untagged resources may be removed by the account cleanup policy."
  type        = string

  validation {
    condition     = length(trimspace(var.owner)) > 0
    error_message = "owner must be set; every resource needs the Owner tag."
  }
}

variable "project_name" {
  description = "Project name used for tags."
  type        = string
  default     = "fileshare"
}

variable "state_bucket_name" {
  description = "Globally unique name of the Terraform state bucket. Put the same value in each layer's backend.hcl."
  type        = string
}

variable "noncurrent_version_days" {
  description = "Days to keep previous state versions before they expire."
  type        = number
  default     = 90
}

variable "noncurrent_versions_to_keep" {
  description = "Number of most recent previous state versions kept regardless of age."
  type        = number
  default     = 20
}
