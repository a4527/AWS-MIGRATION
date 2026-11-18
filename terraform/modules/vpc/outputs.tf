output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private subnet IDs."
  value       = aws_subnet.private[*].id
}

output "database_subnet_ids" {
  description = "Database subnet IDs."
  value       = aws_subnet.database[*].id
}

output "database_route_table_id" {
  description = "Route table ID for database subnets."
  value       = aws_route_table.database.id
}

output "private_route_table_id" {
  description = "Route table ID for private subnets. The application layer adds the NAT default route."
  value       = aws_route_table.private.id
}
