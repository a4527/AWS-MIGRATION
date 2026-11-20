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

output "task_definition_template_arn" {
  description = "Terraform-managed task definition template ARN; not necessarily the revision deployed by CD."
  value       = aws_ecs_task_definition.application.arn
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

output "alb_zone_id" {
  description = "Public ALB canonical hosted zone ID."
  value       = aws_lb.application.zone_id
}

output "target_group_arn" {
  description = "Application target group ARN."
  value       = aws_lb_target_group.application.arn
}

output "alb_arn_suffix" {
  description = "ALB ARN suffix used as the CloudWatch LoadBalancer dimension."
  value       = aws_lb.application.arn_suffix
}

output "target_group_arn_suffix" {
  description = "Target group ARN suffix used as the CloudWatch TargetGroup dimension."
  value       = aws_lb_target_group.application.arn_suffix
}

output "log_group_name" {
  description = "Backend CloudWatch log group."
  value       = aws_cloudwatch_log_group.application.name
}
