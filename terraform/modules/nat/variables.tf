variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "public_subnet_id" {
  description = "Public subnet that hosts the NAT gateway."
  type        = string
}

variable "private_route_table_id" {
  description = "Private route table that receives the NAT default route."
  type        = string
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}
