output "alb_security_group_id" {
  description = "Security group ID for the public ALB."
  value       = aws_security_group.alb.id
}

output "application_security_group_id" {
  description = "Security group ID for the application runtime."
  value       = aws_security_group.application.id
}

output "redis_security_group_id" {
  description = "Security group ID for ElastiCache Redis."
  value       = aws_security_group.redis.id
}
