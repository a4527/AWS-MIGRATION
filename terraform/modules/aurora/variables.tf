variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "database_subnet_ids" {
  description = "Database subnet IDs for Aurora."
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID for the Aurora security group."
  type        = string
}

variable "database_name" {
  description = "Initial database name."
  type        = string
}

variable "master_username" {
  description = "Aurora master username."
  type        = string
}

variable "master_password_wo" {
  description = "Aurora master password, write-only. Pass an ephemeral value."
  type        = string
  sensitive   = true
  ephemeral   = true
}

variable "master_password_wo_version" {
  description = "Increase to send master_password_wo to Aurora again (rotation)."
  type        = number
}

variable "engine_version" {
  description = "Aurora PostgreSQL engine version."
  type        = string
}

variable "instance_count" {
  description = "Number of Aurora instances."
  type        = number
}

variable "min_capacity" {
  description = "Aurora Serverless v2 minimum ACUs. 0 enables auto-pause."
  type        = number

  validation {
    condition     = var.min_capacity >= 0
    error_message = "min_capacity must be 0 or greater."
  }
}

variable "seconds_until_auto_pause" {
  description = "Idle seconds before Aurora pauses when min_capacity is 0 (300-86400)."
  type        = number
  default     = 300

  validation {
    condition     = var.seconds_until_auto_pause >= 300 && var.seconds_until_auto_pause <= 86400
    error_message = "seconds_until_auto_pause must be between 300 and 86400."
  }
}

variable "deletion_protection" {
  description = "Whether the cluster is protected from deletion."
  type        = bool
  default     = true
}

variable "max_capacity" {
  description = "Aurora Serverless v2 maximum ACUs."
  type        = number
}

variable "skip_final_snapshot" {
  description = "Whether to skip final snapshot when deleting the cluster."
  type        = bool
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
