variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "oidc_provider_arn" {
  description = "GitHub Actions OIDC provider ARN."
  type        = string
}

variable "github_repository" {
  description = "Repository allowed to run Terraform, owner/repo."
  type        = string
}

variable "github_branch" {
  description = "Branch whose runs may plan (drift detection)."
  type        = string
  default     = "main"
}

variable "apply_environment" {
  description = "GitHub environment (with required reviewers) whose jobs may apply."
  type        = string
  default     = "terraform-dev"
}

variable "state_bucket_name" {
  description = "Terraform state bucket (terraform/bootstrap)."
  type        = string
}

variable "file_bucket_name" {
  description = "Uploaded file bucket that plan must not read."
  type        = string
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
