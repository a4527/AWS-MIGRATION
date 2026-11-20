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
