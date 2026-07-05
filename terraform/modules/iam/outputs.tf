output "application_files_policy_arn" {
  description = "IAM policy ARN for S3 file access."
  value       = aws_iam_policy.application_files.arn
}
