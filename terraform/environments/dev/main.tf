locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = merge(var.tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Owner       = var.owner
  })

  https_enabled                 = var.domain_name != "" && var.route53_hosted_zone_name != ""
  create_application_dns_record = local.https_enabled && var.create_application_dns_record
}

data "aws_route53_zone" "application" {
  count = local.https_enabled ? 1 : 0

  name         = var.route53_hosted_zone_name
  private_zone = false
}

resource "aws_acm_certificate" "application" {
  count = local.https_enabled ? 1 : 0

  domain_name       = var.domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = local.common_tags
}

resource "aws_route53_record" "certificate_validation" {
  for_each = local.https_enabled ? {
    for option in aws_acm_certificate.application[0].domain_validation_options : option.domain_name => {
      name   = option.resource_record_name
      record = option.resource_record_value
      type   = option.resource_record_type
    }
  } : {}

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.application[0].zone_id
}

resource "aws_acm_certificate_validation" "application" {
  count = local.https_enabled ? 1 : 0

  certificate_arn         = aws_acm_certificate.application[0].arn
  validation_record_fqdns = [for record in aws_route53_record.certificate_validation : record.fqdn]
}

module "nat" {
  source = "../../modules/nat"

  name_prefix            = local.name_prefix
  public_subnet_id       = local.public_subnet_ids[0]
  private_route_table_id = data.aws_route_table.private.id
  tags                   = local.common_tags
}

module "iam" {
  source = "../../modules/iam"

  name_prefix        = local.name_prefix
  file_bucket_arn    = data.aws_s3_bucket.files.arn
  file_object_prefix = var.file_processor_object_prefix
  tags               = local.common_tags
}

module "file_processor" {
  source = "../../modules/file-processor"

  name_prefix                      = local.name_prefix
  source_file_path                 = "${path.module}/../../functions/file-processor/handler.py"
  file_bucket_id                   = data.aws_s3_bucket.files.id
  file_bucket_arn                  = data.aws_s3_bucket.files.arn
  object_prefix                    = var.file_processor_object_prefix
  blocked_extensions               = var.file_processor_blocked_extensions
  memory_size                      = var.file_processor_memory_size
  timeout_seconds                  = var.file_processor_timeout_seconds
  log_retention_in_days            = var.cloudwatch_log_retention_in_days
  duration_alarm_threshold_ms      = var.file_processor_duration_alarm_threshold_ms
  alarm_action_arns                = [data.aws_sns_topic.alarms.arn]
  enable_eventbridge_notifications = true
  tags                             = local.common_tags
}

module "security_groups" {
  source = "../../modules/security-groups"

  name_prefix              = local.name_prefix
  vpc_id                   = data.aws_vpc.this.id
  aurora_security_group_id = data.aws_security_group.aurora.id
  tags                     = local.common_tags
}

# SEC-01: generated during apply and written to SSM through a write-only
# argument, so it never reaches tfvars, state, or plan. Created and destroyed
# with this layer; tokens issued in an earlier lab stop working.
ephemeral "random_password" "app_jwt_secret" {
  length  = 64
  special = false
}

resource "aws_ssm_parameter" "app_jwt_secret" {
  name             = "/${var.project_name}/${var.environment}/app/jwt-secret"
  description      = "JWT signing secret injected into the backend task (SEC-01)."
  type             = "SecureString"
  value_wo         = ephemeral.random_password.app_jwt_secret.result
  value_wo_version = var.app_jwt_secret_version

  tags = local.common_tags
}

module "ecs" {
  source = "../../modules/ecs"

  name_prefix                     = local.name_prefix
  vpc_id                          = data.aws_vpc.this.id
  public_subnet_ids               = local.public_subnet_ids
  private_subnet_ids              = local.private_subnet_ids
  alb_security_group_id           = module.security_groups.alb_security_group_id
  application_security_group_id   = module.security_groups.application_security_group_id
  container_image                 = "${data.aws_ecr_repository.backend.repository_url}:${var.ecs_backend_image_tag}"
  container_port                  = var.ecs_container_port
  cpu                             = var.ecs_task_cpu
  memory                          = var.ecs_task_memory
  desired_count                   = var.ecs_desired_count
  min_capacity                    = var.ecs_min_capacity
  max_capacity                    = var.ecs_max_capacity
  cpu_target_value                = var.ecs_cpu_target_value
  memory_target_value             = var.ecs_memory_target_value
  log_retention_in_days           = var.cloudwatch_log_retention_in_days
  health_check_path               = var.ecs_health_check_path
  certificate_arn                 = local.https_enabled ? aws_acm_certificate_validation.application[0].certificate_arn : null
  ssl_policy                      = var.alb_ssl_policy
  aws_region                      = var.aws_region
  s3_bucket_name                  = data.aws_s3_bucket.files.bucket
  s3_object_prefix                = var.file_processor_object_prefix
  database_url                    = "jdbc:postgresql://${data.aws_rds_cluster.this.endpoint}:5432/${data.aws_rds_cluster.this.database_name}"
  database_username               = data.aws_rds_cluster.this.master_username
  database_password_parameter_arn = local.aurora_master_password_parameter_arn
  redis_host                      = module.elasticache.primary_endpoint_address
  app_jwt_secret_parameter_arn    = aws_ssm_parameter.app_jwt_secret.arn
  app_policy_arns                 = [module.iam.application_files_policy_arn, aws_iam_policy.scan_results_consumer.arn]
  alarm_action_arns               = [data.aws_sns_topic.alarms.arn]
  staging_enabled                 = var.ecs_staging_enabled
  extra_environment = {
    # FILE-03: GuardDuty verdicts arrive through SQS.
    APP_FILE_SCAN_MODE      = "guardduty"
    APP_FILE_SCAN_QUEUE_URL = aws_sqs_queue.scan_results.id
  }
  tags = local.common_tags

  # Tasks pull images and reach AWS APIs through the NAT route.
  depends_on = [module.nat]
}

resource "aws_route53_record" "application" {
  count = local.create_application_dns_record ? 1 : 0

  name    = var.domain_name
  type    = "A"
  zone_id = data.aws_route53_zone.application[0].zone_id

  alias {
    evaluate_target_health = true
    name                   = module.ecs.alb_dns_name
    zone_id                = module.ecs.alb_zone_id
  }
}

module "elasticache" {
  source = "../../modules/elasticache"

  name_prefix         = local.name_prefix
  database_subnet_ids = local.database_subnet_ids
  security_group_ids  = [module.security_groups.redis_security_group_id]
  node_type           = var.redis_node_type
  num_cache_clusters  = var.redis_num_cache_clusters
  tags                = local.common_tags
}
