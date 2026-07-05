variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
}

variable "availability_zones" {
  description = "Availability zones used by subnet tiers."
  type        = list(string)
}

variable "public_subnets" {
  description = "Public subnet CIDR blocks."
  type        = list(string)
}

variable "private_subnets" {
  description = "Private subnet CIDR blocks."
  type        = list(string)
}

variable "database_subnets" {
  description = "Database subnet CIDR blocks."
  type        = list(string)
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
