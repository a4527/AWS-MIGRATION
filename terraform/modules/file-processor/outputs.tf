output "function_name" {
  description = "File processor Lambda function name."
  value       = aws_lambda_function.this.function_name
}

output "function_arn" {
  description = "File processor Lambda function ARN."
  value       = aws_lambda_function.this.arn
}

output "role_arn" {
  description = "File processor Lambda execution role ARN."
  value       = aws_iam_role.this.arn
}

output "log_group_name" {
  description = "File processor Lambda CloudWatch log group name."
  value       = aws_cloudwatch_log_group.this.name
}
