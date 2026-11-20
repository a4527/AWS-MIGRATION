resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-aurora"
  subnet_ids = var.database_subnet_ids

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-aurora"
  })
}

# Owned by the persistent data layer. The application layer adds the ingress
# rule from its runtime security group, so no rule remains after it is destroyed.
resource "aws_security_group" "this" {
  name        = "${var.name_prefix}-aurora"
  description = "Allows PostgreSQL traffic from the application runtime."
  vpc_id      = var.vpc_id

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-aurora"
  })
}

resource "aws_rds_cluster" "this" {
  cluster_identifier = "${var.name_prefix}-aurora"
  engine             = "aurora-postgresql"
  engine_mode        = "provisioned"
  engine_version     = var.engine_version
  database_name      = var.database_name
  master_username    = var.master_username
  # Write-only: the value never reaches state or plan. It is sent only when
  # master_password_wo_version changes (SEC-01).
  master_password_wo         = var.master_password_wo
  master_password_wo_version = var.master_password_wo_version
  db_subnet_group_name       = aws_db_subnet_group.this.name
  vpc_security_group_ids     = [aws_security_group.this.id]
  storage_encrypted          = true
  copy_tags_to_snapshot      = true
  backup_retention_period    = 1
  deletion_protection        = var.deletion_protection
  skip_final_snapshot        = var.skip_final_snapshot
  final_snapshot_identifier  = var.skip_final_snapshot ? null : "${var.name_prefix}-aurora-final"

  serverlessv2_scaling_configuration {
    min_capacity = var.min_capacity
    max_capacity = var.max_capacity
    # Only applies when min_capacity is 0 (Aurora auto-pause).
    seconds_until_auto_pause = var.min_capacity == 0 ? var.seconds_until_auto_pause : null
  }

  tags = var.tags
}

resource "aws_rds_cluster_instance" "this" {
  count = var.instance_count

  identifier         = "${var.name_prefix}-aurora-${count.index + 1}"
  cluster_identifier = aws_rds_cluster.this.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.this.engine
  engine_version     = aws_rds_cluster.this.engine_version

  tags = var.tags
}
