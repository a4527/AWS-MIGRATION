variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "file_bucket_arn" {
  description = "S3 bucket ARN for file uploads."
  type        = string
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
