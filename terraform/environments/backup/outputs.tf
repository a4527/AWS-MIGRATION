output "bucket_name" {
  description = "Backup bucket; set as backup_bucket_name in ../dev-data."
  value       = aws_s3_bucket.backup.bucket
}

output "bucket_arn" {
  description = "Backup bucket ARN."
  value       = aws_s3_bucket.backup.arn
}
