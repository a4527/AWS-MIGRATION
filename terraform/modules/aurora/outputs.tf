output "cluster_arn" {
  description = "Aurora cluster ARN."
  value       = aws_rds_cluster.this.arn
}

output "cluster_endpoint" {
  description = "Aurora writer endpoint."
  value       = aws_rds_cluster.this.endpoint
}

output "reader_endpoint" {
  description = "Aurora reader endpoint."
  value       = aws_rds_cluster.this.reader_endpoint
}

output "database_name" {
  description = "Aurora database name."
  value       = aws_rds_cluster.this.database_name
}

output "master_username" {
  description = "Aurora master username."
  value       = aws_rds_cluster.this.master_username
}

output "security_group_id" {
  description = "Security group ID attached to Aurora."
  value       = aws_security_group.this.id
}
