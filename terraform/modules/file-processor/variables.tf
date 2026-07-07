variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "source_file_path" {
  description = "Path to the Lambda handler source file."
  type        = string
}

variable "file_bucket_id" {
  description = "S3 bucket ID used for uploaded files."
  type        = string
}

variable "file_bucket_arn" {
  description = "S3 bucket ARN used for uploaded files."
  type        = string
}

variable "object_prefix" {
  description = "S3 object key prefix that triggers file processing."
  type        = string
  default     = ""
}

variable "blocked_extensions" {
  description = "File extensions marked as quarantined by the basic processor."
  type        = list(string)
  default     = [".exe", ".bat", ".cmd", ".sh"]
}

variable "memory_size" {
  description = "Lambda memory size in MiB."
  type        = number
  default     = 256
}

variable "timeout_seconds" {
  description = "Lambda timeout in seconds."
  type        = number
  default     = 30
}

variable "log_retention_in_days" {
  description = "CloudWatch Logs retention in days."
  type        = number
  default     = 14
}

variable "duration_alarm_threshold_ms" {
  description = "CloudWatch alarm threshold for Lambda p95 duration in milliseconds."
  type        = number
  default     = 10000
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
