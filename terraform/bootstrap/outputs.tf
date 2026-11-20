output "state_bucket_name" {
  description = "Put this value in terraform/environments/*/backend.hcl as bucket."
  value       = aws_s3_bucket.state.bucket
}

output "state_bucket_arn" {
  description = "ARN of the Terraform state bucket."
  value       = aws_s3_bucket.state.arn
}
