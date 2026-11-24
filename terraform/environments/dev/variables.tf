variable "aws_region" {
  description = "AWS region for the development environment."
  type        = string
  default     = "us-east-1"
}

variable "owner" {
  description = "Required Owner tag on every resource. Untagged resources may be removed by the account cleanup policy."
  type        = string

  validation {
    condition     = length(trimspace(var.owner)) > 0
    error_message = "owner must be set; every resource needs the Owner tag."
  }
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

variable "file_bucket_name" {
  description = "Existing file bucket created by the data layer (../dev-data)."
  type        = string
}

variable "ecs_backend_image_tag" {
  description = "ECR image tag in the Terraform task definition template; CD manages the deployed service revision."
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
  description = "Initial ECS task count; Application Auto Scaling manages it after service creation."
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

variable "ecs_deregistration_delay_seconds" {
  description = "Production target group deregistration delay (ARCH-01; ALB default 300)."
  type        = number
  default     = 30
}

variable "s3_gateway_endpoint_enabled" {
  description = "Route S3 traffic from private subnets through a free gateway endpoint instead of the NAT gateway (ARCH-01)."
  type        = bool
  default     = true
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

variable "app_jwt_secret_version" {
  description = "Increase to generate a new JWT secret in SSM. Running tasks keep the old value until a new deployment (SEC-01)."
  type        = number
  default     = 1
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

variable "ecs_staging_enabled" {
  description = "Create the staging ECS service used by CD before promotion (CICD-02, DEC-017)."
  type        = bool
  default     = true
}
