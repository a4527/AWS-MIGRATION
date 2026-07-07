variable "aws_region" {
  description = "AWS region for the development environment."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "Project name used for resource naming and tags."
  type        = string
  default     = "fileshare"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones used by this environment."
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnets" {
  description = "Public subnet CIDR blocks for ALB and NAT gateways."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]
}

variable "private_subnets" {
  description = "Private subnet CIDR blocks for ECS application tasks."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]
}

variable "database_subnets" {
  description = "Isolated subnet CIDR blocks for Aurora and ElastiCache."
  type        = list(string)
  default     = ["10.20.20.0/24", "10.20.21.0/24"]
}

variable "file_bucket_name" {
  description = "Globally unique S3 bucket name for uploaded files."
  type        = string
  default     = "fileshare-dev-uploaded-files"
}

variable "force_destroy_buckets" {
  description = "Whether Terraform can delete non-empty development buckets."
  type        = bool
  default     = false
}

variable "ecs_backend_image_tag" {
  description = "ECR image tag deployed to the ECS backend service."
  type        = string
  default     = "latest"
}

variable "ecs_container_port" {
  description = "Container port exposed by the backend application."
  type        = number
  default     = 8080
}

variable "ecs_task_cpu" {
  description = "Fargate task CPU units for the backend service."
  type        = number
  default     = 512
}

variable "ecs_task_memory" {
  description = "Fargate task memory in MiB for the backend service."
  type        = number
  default     = 1024
}

variable "ecs_desired_count" {
  description = "Desired ECS task count for the backend service."
  type        = number
  default     = 2
}

variable "ecs_min_capacity" {
  description = "Minimum ECS service task count for auto scaling."
  type        = number
  default     = 2
}

variable "ecs_max_capacity" {
  description = "Maximum ECS service task count for auto scaling."
  type        = number
  default     = 4
}

variable "ecs_cpu_target_value" {
  description = "Target average CPU utilization percentage for ECS service auto scaling."
  type        = number
  default     = 60
}

variable "ecs_memory_target_value" {
  description = "Target average memory utilization percentage for ECS service auto scaling."
  type        = number
  default     = 70
}

variable "ecs_health_check_path" {
  description = "ALB health check path for the ECS backend service."
  type        = string
  default     = "/api/health"
}

variable "domain_name" {
  description = "Application DNS name, for example api.example.com. Leave empty to disable Route53 and HTTPS."
  type        = string
  default     = ""
}

variable "route53_hosted_zone_name" {
  description = "Public Route53 hosted zone name, for example example.com. Leave empty to disable Route53 and HTTPS."
  type        = string
  default     = ""
}

variable "create_application_dns_record" {
  description = "Whether Terraform creates the Route53 alias record from the application domain to the ALB."
  type        = bool
  default     = true
}

variable "alb_ssl_policy" {
  description = "ALB HTTPS listener SSL policy."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

variable "cloudwatch_log_retention_in_days" {
  description = "CloudWatch log retention in days for application and serverless logs."
  type        = number
  default     = 14
}

variable "file_processor_object_prefix" {
  description = "S3 object key prefix that triggers the file processor Lambda."
  type        = string
  default     = "files/"
}

variable "file_processor_blocked_extensions" {
  description = "File extensions marked as quarantined by the file processor Lambda."
  type        = list(string)
  default     = [".exe", ".bat", ".cmd", ".sh"]
}

variable "file_processor_memory_size" {
  description = "File processor Lambda memory size in MiB."
  type        = number
  default     = 256
}

variable "file_processor_timeout_seconds" {
  description = "File processor Lambda timeout in seconds."
  type        = number
  default     = 30
}

variable "file_processor_duration_alarm_threshold_ms" {
  description = "CloudWatch alarm threshold for file processor Lambda p95 duration in milliseconds."
  type        = number
  default     = 10000
}

variable "app_jwt_secret" {
  description = "JWT secret used by the backend application."
  type        = string
  sensitive   = true
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy through OIDC, in owner/repo format."
  type        = string
}

variable "github_branch" {
  description = "GitHub branch allowed to deploy through OIDC."
  type        = string
  default     = "main"
}

variable "github_oidc_thumbprints" {
  description = "Thumbprints for the GitHub Actions OIDC provider."
  type        = list(string)
  default     = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

variable "aurora_database_name" {
  description = "Initial Aurora PostgreSQL database name."
  type        = string
  default     = "fileshare"
}

variable "aurora_master_username" {
  description = "Aurora PostgreSQL master username."
  type        = string
  default     = "fileshare"
}

variable "aurora_master_password" {
  description = "Aurora PostgreSQL master password."
  type        = string
  sensitive   = true
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL engine version."
  type        = string
  default     = "16.6"
}

variable "aurora_instance_count" {
  description = "Number of Aurora instances."
  type        = number
  default     = 1
}

variable "aurora_min_capacity" {
  description = "Aurora Serverless v2 minimum ACUs."
  type        = number
  default     = 0.5
}

variable "aurora_max_capacity" {
  description = "Aurora Serverless v2 maximum ACUs."
  type        = number
  default     = 2
}

variable "aurora_skip_final_snapshot" {
  description = "Whether to skip a final snapshot when deleting the development Aurora cluster."
  type        = bool
  default     = true
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_num_cache_clusters" {
  description = "Number of Redis cache nodes."
  type        = number
  default     = 1
}

variable "tags" {
  description = "Additional tags applied to resources."
  type        = map(string)
  default     = {}
}
