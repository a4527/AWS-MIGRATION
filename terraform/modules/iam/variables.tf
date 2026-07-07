variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "file_bucket_arn" {
  description = "S3 bucket ARN for file uploads."
  type        = string
}

variable "file_object_prefix" {
  description = "S3 object key prefix the application can access."
  type        = string
  default     = "files/"
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
