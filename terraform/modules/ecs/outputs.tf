output "cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  description = "ECS cluster ARN."
  value       = aws_ecs_cluster.this.arn
}

output "service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.application.name
}

output "service_arn" {
  description = "ECS service ARN."
  value       = aws_ecs_service.application.id
}

output "task_role_arn" {
  description = "IAM role ARN used by the application ECS task."
  value       = aws_iam_role.task.arn
}

output "execution_role_arn" {
  description = "IAM role ARN used by ECS to pull images and publish logs."
  value       = aws_iam_role.execution.arn
}

output "alb_dns_name" {
  description = "Public ALB DNS name."
  value       = aws_lb.application.dns_name
}

output "target_group_arn" {
  description = "Application target group ARN."
  value       = aws_lb_target_group.application.arn
}
