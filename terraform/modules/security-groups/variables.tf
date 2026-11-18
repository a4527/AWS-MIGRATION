variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "aurora_security_group_id" {
  description = "Aurora security group owned by the persistent data layer."
  type        = string
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
