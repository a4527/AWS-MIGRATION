# Persistent data layer: network base, file bucket, and Aurora.
# Kept between labs; the application layer (../dev) is created and destroyed
# around it and looks these resources up by name.

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # The application layer builds the same name to reference the parameter.
  aurora_master_password_parameter_name = "/${var.project_name}/${var.environment}/aurora/master-password"

  common_tags = merge(var.tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Owner       = var.owner
    Layer       = "data"
  })
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

module "s3" {
  source = "../../modules/s3"

  bucket_name   = var.file_bucket_name
  force_destroy = var.force_destroy_buckets
  tags          = local.common_tags
}

# SEC-01: the master password is generated during apply and written only to
# Aurora and SSM through write-only arguments, so it never reaches tfvars,
# state, or plan. Increase aurora_master_password_version to rotate both.
# Characters that Spring placeholders, JDBC URLs, or Aurora reject are excluded.
ephemeral "random_password" "aurora_master" {
  length           = 40
  special          = true
  override_special = "-_"
}

resource "aws_ssm_parameter" "aurora_master_password" {
  name             = local.aurora_master_password_parameter_name
  description      = "Aurora master password read by the application layer (SEC-01)."
  type             = "SecureString"
  value_wo         = ephemeral.random_password.aurora_master.result
  value_wo_version = var.aurora_master_password_version

  tags = local.common_tags
}

module "aurora" {
  source = "../../modules/aurora"

  name_prefix                = local.name_prefix
  vpc_id                     = module.vpc.vpc_id
  database_subnet_ids        = module.vpc.database_subnet_ids
  database_name              = var.aurora_database_name
  master_username            = var.aurora_master_username
  master_password_wo         = ephemeral.random_password.aurora_master.result
  master_password_wo_version = var.aurora_master_password_version
  engine_version             = var.aurora_engine_version
  instance_count             = var.aurora_instance_count
  min_capacity               = var.aurora_min_capacity
  max_capacity               = var.aurora_max_capacity
  seconds_until_auto_pause   = var.aurora_seconds_until_auto_pause
  deletion_protection        = var.aurora_deletion_protection
  skip_final_snapshot        = var.aurora_skip_final_snapshot
  tags                       = local.common_tags
}

# DEC-015: the deploy role and ECR live in the persistent layer so that CD can
# authenticate, push, and detect whether the application layer is up even when
# ../dev is destroyed, and so a recreated lab starts from the last pushed image.
module "ecr" {
  source = "../../modules/ecr"

  name_prefix = local.name_prefix
  repositories = {
    backend = {
      image_tag_mutability = "MUTABLE"
      scan_on_push         = true
      # Images are rebuildable artifacts; do not block a data-layer teardown.
      force_delete = true
    }
  }
  tags = local.common_tags
}

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
  arn_prefix = "arn:${data.aws_partition.current.partition}"
  account_id = data.aws_caller_identity.current.account_id

  # Names created by ../dev (modules/ecs). Built here because that layer may not exist.
  ecs_cluster_arn        = "${local.arn_prefix}:ecs:${var.aws_region}:${local.account_id}:cluster/${local.name_prefix}-ecs"
  ecs_service_arn        = "${local.arn_prefix}:ecs:${var.aws_region}:${local.account_id}:service/${local.name_prefix}-ecs/${local.name_prefix}-backend"
  ecs_task_role_arn      = "${local.arn_prefix}:iam::${local.account_id}:role/${local.name_prefix}-ecs-task"
  ecs_execution_role_arn = "${local.arn_prefix}:iam::${local.account_id}:role/${local.name_prefix}-ecs-execution"
}

module "github_actions" {
  source = "../../modules/github-actions"

  name_prefix                 = local.name_prefix
  github_repository           = var.github_repository
  github_branch               = var.github_branch
  github_oidc_thumbprints     = var.github_oidc_thumbprints
  ecr_repository_arn          = module.ecr.repository_arns["backend"]
  ecs_cluster_arn             = local.ecs_cluster_arn
  ecs_service_arn             = local.ecs_service_arn
  extra_ecs_service_arns      = ["${local.arn_prefix}:ecs:${var.aws_region}:${local.account_id}:service/${local.name_prefix}-ecs/${local.name_prefix}-backend-staging"]
  ecs_task_role_arn           = local.ecs_task_role_arn
  ecs_execution_role_arn      = local.ecs_execution_role_arn
  aurora_cluster_arn          = module.aurora.cluster_arn
  aurora_instance_arn_pattern = "${local.arn_prefix}:rds:${var.aws_region}:${local.account_id}:db:${local.name_prefix}-aurora-*"
  tags                        = local.common_tags
}

# OBS-02: alarm notifications. Kept in the persistent layer so the email
# subscription is confirmed once, not after every lab. Recipients are the
# budget alert addresses. Not KMS-encrypted: CloudWatch alarms publish to it
# without an extra key policy.
resource "aws_sns_topic" "alarms" {
  name = "${local.name_prefix}-alarms"

  tags = local.common_tags
}

resource "aws_sns_topic_subscription" "alarm_email" {
  for_each = toset(var.budget_alert_emails)

  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = each.value
}

# IAC-03: Terraform CI roles (plan/drift read-only, apply after approval).
module "terraform_ci" {
  source = "../../modules/terraform-ci"

  name_prefix       = local.name_prefix
  oidc_provider_arn = module.github_actions.oidc_provider_arn
  github_repository = var.github_repository
  github_branch     = var.github_branch
  state_bucket_name = var.terraform_state_bucket_name
  file_bucket_name  = var.file_bucket_name
  tags              = local.common_tags
}

# Account cost guard (PORT-01). Kept in the persistent layer so it survives lab teardown.
resource "aws_budgets_budget" "monthly" {
  name         = "${local.name_prefix}-monthly"
  budget_type  = "COST"
  limit_amount = format("%.2f", var.budget_limit_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  dynamic "cost_filter" {
    for_each = var.budget_filter_by_owner_tag ? [1] : []

    content {
      name = "TagKeyValue"
      # Cost Explorer tag filter format: user:<key>$<value>
      values = [format("user:Owner$%s", var.owner)]
    }
  }

  dynamic "notification" {
    for_each = var.budget_alert_thresholds

    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = var.budget_alert_emails
    }
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = var.budget_alert_emails
  }

  tags = local.common_tags
}
