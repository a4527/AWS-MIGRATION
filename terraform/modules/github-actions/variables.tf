variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the deploy role, in owner/repo format."
  type        = string
}

variable "github_branch" {
  description = "GitHub branch allowed to assume the deploy role."
  type        = string
  default     = "main"
}

variable "github_oidc_thumbprints" {
  description = "Thumbprints for the GitHub Actions OIDC provider."
  type        = list(string)
  default     = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

variable "ecr_repository_arn" {
  description = "ECR repository ARN that GitHub Actions can push to."
  type        = string
}

variable "ecs_cluster_arn" {
  description = "ECS cluster ARN used by the backend service."
  type        = string
}

variable "ecs_service_arn" {
  description = "ECS service ARN that GitHub Actions can update."
  type        = string
}

variable "ecs_task_role_arn" {
  description = "ECS application task role ARN allowed for iam:PassRole."
  type        = string
}

variable "ecs_execution_role_arn" {
  description = "ECS task execution role ARN allowed for iam:PassRole."
  type        = string
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
