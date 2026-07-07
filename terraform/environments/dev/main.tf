locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = merge(var.tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
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

module "vpc" {
  source = "../../modules/vpc"

  name_prefix        = local.name_prefix
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
  public_subnets     = var.public_subnets
  private_subnets    = var.private_subnets
  database_subnets   = var.database_subnets
  tags               = local.common_tags
}

module "ecr" {
  source = "../../modules/ecr"

  name_prefix = local.name_prefix
  repositories = {
    backend = {
      image_tag_mutability = "MUTABLE"
      scan_on_push         = true
      force_delete         = true
    }
  }
  tags = local.common_tags
}

module "s3" {
  source = "../../modules/s3"

  bucket_name   = var.file_bucket_name
  force_destroy = var.force_destroy_buckets
  tags          = local.common_tags
}

module "iam" {
  source = "../../modules/iam"

  name_prefix        = local.name_prefix
  file_bucket_arn    = module.s3.bucket_arn
  file_object_prefix = var.file_processor_object_prefix
  tags               = local.common_tags
}

module "file_processor" {
  source = "../../modules/file-processor"

  name_prefix                 = local.name_prefix
  source_file_path            = "${path.module}/../../functions/file-processor/handler.py"
  file_bucket_id              = module.s3.bucket_id
  file_bucket_arn             = module.s3.bucket_arn
  object_prefix               = var.file_processor_object_prefix
  blocked_extensions          = var.file_processor_blocked_extensions
  memory_size                 = var.file_processor_memory_size
  timeout_seconds             = var.file_processor_timeout_seconds
  log_retention_in_days       = var.cloudwatch_log_retention_in_days
  duration_alarm_threshold_ms = var.file_processor_duration_alarm_threshold_ms
  tags                        = local.common_tags
}

module "security_groups" {
  source = "../../modules/security-groups"

  name_prefix = local.name_prefix
  vpc_id      = module.vpc.vpc_id
  tags        = local.common_tags
}

module "ecs" {
  source = "../../modules/ecs"

  name_prefix                   = local.name_prefix
  vpc_id                        = module.vpc.vpc_id
  public_subnet_ids             = module.vpc.public_subnet_ids
  private_subnet_ids            = module.vpc.private_subnet_ids
  alb_security_group_id         = module.security_groups.alb_security_group_id
  application_security_group_id = module.security_groups.application_security_group_id
  container_image               = "${module.ecr.repository_urls["backend"]}:${var.ecs_backend_image_tag}"
  container_port                = var.ecs_container_port
  cpu                           = var.ecs_task_cpu
  memory                        = var.ecs_task_memory
  desired_count                 = var.ecs_desired_count
  min_capacity                  = var.ecs_min_capacity
  max_capacity                  = var.ecs_max_capacity
  cpu_target_value              = var.ecs_cpu_target_value
  memory_target_value           = var.ecs_memory_target_value
  log_retention_in_days         = var.cloudwatch_log_retention_in_days
  health_check_path             = var.ecs_health_check_path
  certificate_arn               = local.https_enabled ? aws_acm_certificate_validation.application[0].certificate_arn : null
  ssl_policy                    = var.alb_ssl_policy
  aws_region                    = var.aws_region
  s3_bucket_name                = module.s3.bucket_name
  s3_object_prefix              = var.file_processor_object_prefix
  database_url                  = "jdbc:postgresql://${module.aurora.cluster_endpoint}:5432/${var.aurora_database_name}"
  database_username             = var.aurora_master_username
  database_password             = var.aurora_master_password
  redis_host                    = module.elasticache.primary_endpoint_address
  app_jwt_secret                = var.app_jwt_secret
  app_policy_arns               = [module.iam.application_files_policy_arn]
  tags                          = local.common_tags
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

module "github_actions" {
  source = "../../modules/github-actions"

  name_prefix             = local.name_prefix
  github_repository       = var.github_repository
  github_branch           = var.github_branch
  github_oidc_thumbprints = var.github_oidc_thumbprints
  ecr_repository_arn      = module.ecr.repository_arns["backend"]
  ecs_cluster_arn         = module.ecs.cluster_arn
  ecs_service_arn         = module.ecs.service_arn
  ecs_task_role_arn       = module.ecs.task_role_arn
  ecs_execution_role_arn  = module.ecs.execution_role_arn
  tags                    = local.common_tags
}

module "aurora" {
  source = "../../modules/aurora"

  name_prefix         = local.name_prefix
  database_subnet_ids = module.vpc.database_subnet_ids
  security_group_ids  = [module.security_groups.aurora_security_group_id]
  database_name       = var.aurora_database_name
  master_username     = var.aurora_master_username
  master_password     = var.aurora_master_password
  engine_version      = var.aurora_engine_version
  instance_count      = var.aurora_instance_count
  min_capacity        = var.aurora_min_capacity
  max_capacity        = var.aurora_max_capacity
  skip_final_snapshot = var.aurora_skip_final_snapshot
  tags                = local.common_tags
}

module "elasticache" {
  source = "../../modules/elasticache"

  name_prefix         = local.name_prefix
  database_subnet_ids = module.vpc.database_subnet_ids
  security_group_ids  = [module.security_groups.redis_security_group_id]
  node_type           = var.redis_node_type
  num_cache_clusters  = var.redis_num_cache_clusters
  tags                = local.common_tags
}
