variable "bucket_name" {
  description = "S3 bucket name."
  type        = string
}

variable "force_destroy" {
  description = "Whether Terraform can delete a non-empty bucket."
  type        = bool
  default     = false
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
