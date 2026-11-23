output "trail_arn" {
  description = "CloudTrail trail that feeds the change rules."
  value       = aws_cloudtrail.this.arn
}

output "trail_bucket_name" {
  description = "S3 bucket for the trail's log files."
  value       = aws_s3_bucket.trail.id
}

output "network_change_rule_name" {
  description = "EventBridge rule for security group rule and route changes."
  value       = aws_cloudwatch_event_rule.network_change.name
}
