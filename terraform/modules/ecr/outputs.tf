output "repository_urls" {
  description = "Repository URLs keyed by logical name."
  value       = { for key, repo in aws_ecr_repository.this : key => repo.repository_url }
}

output "repository_arns" {
  description = "Repository ARNs keyed by logical name."
  value       = { for key, repo in aws_ecr_repository.this : key => repo.arn }
}
