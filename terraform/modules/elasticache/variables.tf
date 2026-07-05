variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "database_subnet_ids" {
  description = "Database subnet IDs for ElastiCache."
  type        = list(string)
}

variable "security_group_ids" {
  description = "Security groups attached to ElastiCache."
  type        = list(string)
}

variable "node_type" {
  description = "ElastiCache node type."
  type        = string
}

variable "num_cache_clusters" {
  description = "Number of cache nodes."
  type        = number
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
