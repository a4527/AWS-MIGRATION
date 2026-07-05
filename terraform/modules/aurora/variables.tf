variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "database_subnet_ids" {
  description = "Database subnet IDs for Aurora."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups attached to Aurora."
  type        = list(string)
}

variable "database_name" {
  description = "Initial database name."
  type        = string
}

variable "master_username" {
  description = "Aurora master username."
  type        = string
}

variable "master_password" {
  description = "Aurora master password."
  type        = string
  sensitive   = true
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
  description = "Aurora Serverless v2 minimum ACUs."
  type        = number
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
