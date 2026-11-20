output "vpc_id" {
  description = "VPC ID."
  value       = data.aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = local.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private subnet IDs."
  value       = local.private_subnet_ids
}

output "database_subnet_ids" {
  description = "Database subnet IDs."
  value       = local.database_subnet_ids
}

output "backend_ecr_repository_url" {
  description = "ECR repository URL for the Spring Boot backend image."
  value       = data.aws_ecr_repository.backend.repository_url
}

output "file_bucket_name" {
  description = "S3 bucket used for uploaded files."
  value       = data.aws_s3_bucket.files.bucket
}

output "application_files_policy_arn" {
  description = "IAM policy ARN for application S3 file access."
  value       = module.iam.application_files_policy_arn
}

output "alb_security_group_id" {
  description = "Security group ID for the future public ALB."
  value       = module.security_groups.alb_security_group_id
}

output "application_security_group_id" {
  description = "Security group ID for the application runtime."
  value       = module.security_groups.application_security_group_id
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = module.ecs.cluster_name
}

output "ecs_service_name" {
  description = "ECS backend service name."
  value       = module.ecs.service_name
}

output "application_role_arn" {
  description = "ECS task role ARN used by the application."
  value       = module.ecs.task_role_arn
}

output "application_alb_dns_name" {
  description = "Public ALB DNS name for the backend application."
  value       = module.ecs.alb_dns_name
}

output "application_url" {
  description = "Application URL. Uses the custom HTTPS domain when configured, otherwise the ALB HTTP endpoint."
  value       = var.domain_name != "" ? "https://${var.domain_name}" : "http://${module.ecs.alb_dns_name}"
}

output "application_domain_name" {
  description = "Custom application domain name when Route53 and HTTPS are enabled."
  value       = var.domain_name
}

output "aurora_cluster_endpoint" {
  description = "Aurora PostgreSQL writer endpoint."
  value       = data.aws_rds_cluster.this.endpoint
}

output "redis_primary_endpoint" {
  description = "ElastiCache Redis primary endpoint."
  value       = module.elasticache.primary_endpoint_address
}

output "file_processor_function_name" {
  description = "S3 event file processor Lambda function name."
  value       = module.file_processor.function_name
}

output "file_processor_log_group_name" {
  description = "CloudWatch log group for the file processor Lambda."
  value       = module.file_processor.log_group_name
}

output "ecs_task_definition_template_arn" {
  description = "Terraform task definition template for initial deployment or explicit configuration rollout."
  value       = module.ecs.task_definition_template_arn
}
