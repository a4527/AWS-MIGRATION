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

output "backend_ecr_repository_url" {
  description = "ECR repository URL for the Spring Boot backend image."
  value       = module.ecr.repository_urls["backend"]
}

output "file_bucket_name" {
  description = "S3 bucket used for uploaded files."
  value       = module.s3.bucket_name
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

output "github_actions_deploy_role_arn" {
  description = "IAM role ARN to store as the GitHub Actions AWS_DEPLOY_ROLE_ARN secret."
  value       = module.github_actions.deploy_role_arn
}

output "application_role_arn" {
  description = "ECS task role ARN used by the application."
  value       = module.ecs.task_role_arn
}

output "application_alb_dns_name" {
  description = "Public ALB DNS name for the backend application."
  value       = module.ecs.alb_dns_name
}

output "aurora_cluster_endpoint" {
  description = "Aurora PostgreSQL writer endpoint."
  value       = module.aurora.cluster_endpoint
}

output "redis_primary_endpoint" {
  description = "ElastiCache Redis primary endpoint."
  value       = module.elasticache.primary_endpoint_address
}
