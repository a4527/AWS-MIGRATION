variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet IDs for the ALB."
  type        = list(string)
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for ECS tasks."
  type        = list(string)
}

variable "alb_security_group_id" {
  description = "Security group ID attached to the public ALB."
  type        = string
}

variable "application_security_group_id" {
  description = "Security group ID attached to ECS tasks."
  type        = string
}

variable "container_image" {
  description = "Container image URI for the application."
  type        = string
}

variable "container_port" {
  description = "Application container port."
  type        = number
  default     = 8080
}

variable "cpu" {
  description = "Fargate task CPU units."
  type        = number
}

variable "memory" {
  description = "Fargate task memory in MiB."
  type        = number
}

variable "desired_count" {
  description = "Initial ECS service task count; Application Auto Scaling manages it after creation."
  type        = number
}

variable "min_capacity" {
  description = "Minimum ECS service task count for auto scaling."
  type        = number
}

variable "max_capacity" {
  description = "Maximum ECS service task count for auto scaling."
  type        = number
}

variable "cpu_target_value" {
  description = "Target average CPU utilization percentage for ECS service auto scaling."
  type        = number
}

variable "memory_target_value" {
  description = "Target average memory utilization percentage for ECS service auto scaling."
  type        = number
}

variable "log_retention_in_days" {
  description = "CloudWatch log retention in days."
  type        = number
  default     = 14
}

variable "health_check_path" {
  description = "ALB target group health check path."
  type        = string
}

variable "certificate_arn" {
  description = "ACM certificate ARN for the HTTPS listener. If null, only HTTP is enabled."
  type        = string
  default     = null
}

variable "ssl_policy" {
  description = "ALB HTTPS listener SSL policy."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}

variable "aws_region" {
  description = "AWS region passed to the application."
  type        = string
}

variable "s3_bucket_name" {
  description = "S3 bucket name used by the application."
  type        = string
}

variable "s3_object_prefix" {
  description = "S3 object key prefix used by the application."
  type        = string
  default     = "files/"
}

variable "database_url" {
  description = "JDBC database URL used by the application."
  type        = string
}

variable "database_username" {
  description = "Database username used by the application."
  type        = string
}

variable "database_password_parameter_arn" {
  description = "ARN of the SSM SecureString parameter holding the database password."
  type        = string
}

variable "redis_host" {
  description = "Redis primary endpoint host."
  type        = string
}

variable "app_jwt_secret_parameter_arn" {
  description = "ARN of the SSM SecureString parameter holding the JWT secret."
  type        = string
}

variable "app_policy_arns" {
  description = "IAM policy ARNs attached to the ECS task role."
  type        = list(string)
  default     = []
}

variable "tags" {
  description = "Common resource tags."
  type        = map(string)
  default     = {}
}

variable "alarm_action_arns" {
  description = "SNS topic ARNs notified when an alarm fires and when it recovers (OBS-02)."
  type        = list(string)
  default     = []
}

variable "staging_enabled" {
  description = "Create a staging service in the same cluster for CICD-02 promotion (DEC-017)."
  type        = bool
  default     = false
}

variable "staging_header_value" {
  description = "Value of the X-Deploy-Stage header that routes a request to the staging service."
  type        = string
  default     = "staging"
}

variable "extra_environment" {
  description = "Additional plain (non-secret) environment variables for the backend container."
  type        = map(string)
  default     = {}
}

variable "deregistration_delay_seconds" {
  description = "Seconds the production target group drains a deregistering task (ALB default 300)."
  type        = number
  default     = 30

  validation {
    condition     = var.deregistration_delay_seconds >= 0 && var.deregistration_delay_seconds <= 3600
    error_message = "deregistration_delay_seconds must be between 0 and 3600."
  }
}

variable "health_check_grace_period_seconds" {
  description = "Seconds ECS ignores ALB health checks after a task starts (startup is about 60 s)."
  type        = number
  default     = 120
}
