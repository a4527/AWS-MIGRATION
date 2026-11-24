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

output "aurora_master_password_parameter_name" {
  description = "SSM SecureString parameter that holds the Aurora master password (value not in state)."
  value       = aws_ssm_parameter.aurora_master_password.name
}

output "aurora_security_group_id" {
  description = "Aurora security group ID."
  value       = module.aurora.security_group_id
}

output "backend_ecr_repository_url" {
  description = "ECR repository URL for the backend image (kept between labs)."
  value       = module.ecr.repository_urls["backend"]
}

output "github_actions_deploy_role_arn" {
  description = "IAM role ARN to store as the GitHub Actions AWS_DEPLOY_ROLE_ARN secret."
  value       = module.github_actions.deploy_role_arn
}

output "alarm_topic_arn" {
  description = "SNS topic for CloudWatch alarm notifications (email subscriptions need confirmation)."
  value       = aws_sns_topic.alarms.arn
}

output "budget_name" {
  description = "AWS Budgets budget name."
  value       = aws_budgets_budget.monthly.name
}

output "terraform_plan_role_arn" {
  description = "GitHub Actions secret AWS_TF_PLAN_ROLE_ARN."
  value       = module.terraform_ci.plan_role_arn
}

output "terraform_apply_role_arn" {
  description = "GitHub Actions secret AWS_TF_APPLY_ROLE_ARN."
  value       = module.terraform_ci.apply_role_arn
}

output "change_alerts_trail_bucket_name" {
  description = "S3 bucket for the SEC-03 CloudTrail trail log files."
  value       = module.change_alerts.trail_bucket_name
}
