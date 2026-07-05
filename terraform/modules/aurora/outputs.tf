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
