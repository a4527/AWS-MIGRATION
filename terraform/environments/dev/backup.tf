# BACKUP-01 (ADR-002): one-off ECS task that runs pg_dump and uploads the dump
# to the file bucket under db_backup_object_prefix, outside files/. Replication
# (../dev-data/backup.tf) then copies it to the personal account. The prefix is
# outside the application, Lambda and GuardDuty scopes, which all use files/.
# Run with scripts/db-backup.sh before tearing this layer down.

locals {
  db_backup_name = "${local.name_prefix}-db-backup"
}

resource "aws_security_group" "db_backup" {
  name        = local.db_backup_name
  description = "One-off pg_dump task: PostgreSQL to Aurora, HTTPS to S3, SSM and image registries."
  vpc_id      = data.aws_vpc.this.id

  egress {
    description     = "PostgreSQL to Aurora"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [data.aws_security_group.aurora.id]
  }

  egress {
    description = "HTTPS to AWS APIs and ECR Public through NAT"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = local.db_backup_name
  })
}

resource "aws_vpc_security_group_ingress_rule" "aurora_from_db_backup" {
  security_group_id            = data.aws_security_group.aurora.id
  description                  = "PostgreSQL from the pg_dump task"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.db_backup.id

  tags = local.common_tags
}

data "aws_iam_policy_document" "db_backup_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "db_backup_task" {
  name               = "${local.db_backup_name}-task"
  assume_role_policy = data.aws_iam_policy_document.db_backup_assume.json

  tags = local.common_tags
}

# Write only, dump prefix only: the task cannot read or overwrite user files.
data "aws_iam_policy_document" "db_backup_task" {
  statement {
    sid       = "PutDumpOnly"
    actions   = ["s3:PutObject"]
    resources = ["${data.aws_s3_bucket.files.arn}/${var.db_backup_object_prefix}*"]
  }
}

resource "aws_iam_role_policy" "db_backup_task" {
  name   = "${local.db_backup_name}-task"
  role   = aws_iam_role.db_backup_task.id
  policy = data.aws_iam_policy_document.db_backup_task.json
}

resource "aws_cloudwatch_log_group" "db_backup" {
  name              = "/ecs/${local.db_backup_name}"
  retention_in_days = var.cloudwatch_log_retention_in_days

  tags = local.common_tags
}

# Two containers share a task-local volume: pg_dump writes the file, then the
# AWS CLI uploads it only if pg_dump exited 0. DUMP_KEY is set per run by
# scripts/db-backup.sh so the script knows the object key.
resource "aws_ecs_task_definition" "db_backup" {
  family                   = local.db_backup_name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  # The backend execution role already reads only the DB password and JWT
  # secret parameters (SEC-01); this task asks it for the DB password only.
  execution_role_arn = module.ecs.execution_role_arn
  task_role_arn      = aws_iam_role.db_backup_task.arn

  volume {
    name = "dump"
  }

  container_definitions = jsonencode([
    {
      name      = "pg-dump"
      image     = var.db_backup_postgres_image
      essential = false
      command   = ["sh", "-c", "pg_dump --format=custom --no-owner --file=/dump/db.dump && ls -l /dump/db.dump"]
      environment = [
        { name = "PGHOST", value = data.aws_rds_cluster.this.endpoint },
        { name = "PGPORT", value = "5432" },
        { name = "PGDATABASE", value = data.aws_rds_cluster.this.database_name },
        { name = "PGUSER", value = data.aws_rds_cluster.this.master_username },
        { name = "PGSSLMODE", value = "require" },
        # Aurora may be paused (0 ACU); resuming takes about 15 seconds.
        { name = "PGCONNECT_TIMEOUT", value = "120" },
      ]
      secrets = [
        { name = "PGPASSWORD", valueFrom = local.aurora_master_password_parameter_arn },
      ]
      mountPoints = [{ sourceVolume = "dump", containerPath = "/dump" }]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.db_backup.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "pg-dump"
        }
      }
    },
    {
      name       = "upload"
      image      = var.db_backup_aws_cli_image
      essential  = true
      entryPoint = ["sh", "-c"]
      command    = ["aws s3 cp /dump/db.dump \"s3://$BUCKET/$${DUMP_KEY:?set by scripts/db-backup.sh}\" --checksum-algorithm SHA256 --only-show-errors && echo uploaded $DUMP_KEY"]
      environment = [
        { name = "BUCKET", value = data.aws_s3_bucket.files.bucket },
        { name = "AWS_REGION", value = var.aws_region },
      ]
      dependsOn   = [{ containerName = "pg-dump", condition = "SUCCESS" }]
      mountPoints = [{ sourceVolume = "dump", containerPath = "/dump", readOnly = true }]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.db_backup.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "upload"
        }
      }
    },
  ])

  tags = local.common_tags
}
