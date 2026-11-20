output "plan_role_arn" {
  description = "Role for terraform plan and drift detection (AWS_TF_PLAN_ROLE_ARN)."
  value       = aws_iam_role.plan.arn
}

output "apply_role_arn" {
  description = "Role for approved terraform apply (AWS_TF_APPLY_ROLE_ARN)."
  value       = aws_iam_role.apply.arn
}
