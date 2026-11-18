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

data "aws_s3_bucket" "files" {
  bucket = var.file_bucket_name
}

locals {
  # aws_subnets does not guarantee order; sort for stable plans.
  public_subnet_ids   = sort(data.aws_subnets.public.ids)
  private_subnet_ids  = sort(data.aws_subnets.private.ids)
  database_subnet_ids = sort(data.aws_subnets.database.ids)
}
