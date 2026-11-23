variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "security_group_ids" {
  description = "Security groups whose rules are not owned by any Terraform resource in this layer (rules are added by the application layer)."
  type        = list(string)
}

variable "route_table_ids" {
  description = "Route tables to watch for route changes."
  type        = list(string)
}

variable "excluded_role_arn" {
  description = "IAM role whose changes are expected (Terraform apply role). Its calls are not reported."
  type        = string
}

variable "topic_arn" {
  description = "SNS topic that receives the notifications."
  type        = string
}

variable "log_retention_days" {
  description = "Days to keep CloudTrail log files in S3."
  type        = number
  default     = 7
}

variable "force_destroy" {
  description = "Allow destroying the trail bucket with log files in it."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags for all resources."
  type        = map(string)
  default     = {}
}
