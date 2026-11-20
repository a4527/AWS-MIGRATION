# Resources owned by the persistent data layer (../dev-data).
# Looked up by name so this layer does not depend on the data layer's state backend.
# Apply ../dev-data first; these lookups fail if it does not exist.

data "aws_vpc" "this" {
  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

data "aws_subnets" "public" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.this.id]
  }

  tags = {
    Tier = "public"
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.this.id]
  }

  tags = {
    Tier = "private"
  }
}

data "aws_subnets" "database" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.this.id]
  }

  tags = {
    Tier = "database"
  }
}

data "aws_route_table" "private" {
  vpc_id = data.aws_vpc.this.id

  tags = {
    Name = "${local.name_prefix}-private-rt"
  }
}

data "aws_security_group" "aurora" {
  vpc_id = data.aws_vpc.this.id
  name   = "${local.name_prefix}-aurora"
}

data "aws_rds_cluster" "this" {
  cluster_identifier = "${local.name_prefix}-aurora"
}

# DEC-015: ECR is in the data layer so images survive lab teardown.
data "aws_ecr_repository" "backend" {
  name = "${local.name_prefix}-backend"
}

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

data "aws_s3_bucket" "files" {
  bucket = var.file_bucket_name
}

locals {
  # aws_subnets does not guarantee order; sort for stable plans.
  public_subnet_ids   = sort(data.aws_subnets.public.ids)
  private_subnet_ids  = sort(data.aws_subnets.private.ids)
  database_subnet_ids = sort(data.aws_subnets.database.ids)

  # SEC-01: the data layer stores the Aurora master password in this SSM
  # parameter. The ARN is built from the name instead of read with a data
  # source, because data "aws_ssm_parameter" would copy the value into state.
  aurora_master_password_parameter_arn = format(
    "arn:%s:ssm:%s:%s:parameter/%s/%s/aurora/master-password",
    data.aws_partition.current.partition,
    var.aws_region,
    data.aws_caller_identity.current.account_id,
    var.project_name,
    var.environment,
  )
}
