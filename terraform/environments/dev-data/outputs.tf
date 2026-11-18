output "vpc_id" {
  description = "VPC ID."
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private subnet IDs."
  value       = module.vpc.private_subnet_ids
}

output "database_subnet_ids" {
  description = "Database subnet IDs."
  value       = module.vpc.database_subnet_ids
}

output "private_route_table_id" {
  description = "Private route table that the application layer attaches the NAT route to."
  value       = module.vpc.private_route_table_id
}

output "file_bucket_name" {
  description = "S3 bucket used for uploaded files."
  value       = module.s3.bucket_name
}

output "aurora_cluster_identifier" {
  description = "Aurora cluster identifier looked up by the application layer."
  value       = "${var.project_name}-${var.environment}-aurora"
}

output "aurora_cluster_endpoint" {
  description = "Aurora PostgreSQL writer endpoint."
  value       = module.aurora.cluster_endpoint
}

output "aurora_security_group_id" {
  description = "Aurora security group ID."
  value       = module.aurora.security_group_id
}

output "budget_name" {
  description = "AWS Budgets budget name."
  value       = aws_budgets_budget.monthly.name
}
