# BACKUP-01 (ADR-002): replicate the file bucket to a bucket in the personal
# AWS account. Only copies leave this account; nothing reads them back at run
# time. Delete markers are not replicated, so a deletion here (or the account
# cleanup) does not remove the copy. Disabled while backup_bucket_name is "".
# The destination bucket and its policy live in ../backup (personal account).

locals {
  backup_enabled        = var.backup_bucket_name != ""
  backup_bucket_arn     = "${local.arn_prefix}:s3:::${var.backup_bucket_name}"
  replication_role_name = "${local.name_prefix}-s3-backup-replication"
}

data "aws_iam_policy_document" "backup_replication_assume" {
  count = local.backup_enabled ? 1 : 0

  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["s3.amazonaws.com"]
    }
    # Confused-deputy protection: only S3 acting for this bucket of this account.
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = [module.s3.bucket_arn]
    }
  }
}

resource "aws_iam_role" "backup_replication" {
  count = local.backup_enabled ? 1 : 0

  name               = local.replication_role_name
  assume_role_policy = data.aws_iam_policy_document.backup_replication_assume[0].json

  tags = local.common_tags
}

data "aws_iam_policy_document" "backup_replication" {
  count = local.backup_enabled ? 1 : 0

  statement {
    sid       = "ReadSourceConfiguration"
    actions   = ["s3:GetReplicationConfiguration", "s3:ListBucket"]
    resources = [module.s3.bucket_arn]
  }

  statement {
    sid       = "ReadSourceVersions"
    actions   = ["s3:GetObjectVersionForReplication", "s3:GetObjectVersionAcl", "s3:GetObjectVersionTagging"]
    resources = ["${module.s3.bucket_arn}/*"]
  }

  # No s3:ReplicateDelete: deletions must not reach the backup.
  statement {
    sid       = "WriteBackupCopies"
    actions   = ["s3:ReplicateObject", "s3:ReplicateTags"]
    resources = ["${local.backup_bucket_arn}/*"]
  }
}

resource "aws_iam_role_policy" "backup_replication" {
  count = local.backup_enabled ? 1 : 0

  name   = local.replication_role_name
  role   = aws_iam_role.backup_replication[0].id
  policy = data.aws_iam_policy_document.backup_replication[0].json
}

resource "aws_s3_bucket_replication_configuration" "backup" {
  count = local.backup_enabled ? 1 : 0

  bucket = module.s3.bucket_id
  role   = aws_iam_role.backup_replication[0].arn

  rule {
    id = "backup-to-personal-account"
    # Pause while generating throwaway data (PERF-01): objects written while
    # paused are never replicated later, so nothing has to be deleted from the
    # backup, where deletions do not propagate.
    status = var.backup_replication_paused ? "Disabled" : "Enabled"

    # Whole bucket: files/ (uploads) and backups/db/ (pg_dump, BACKUP-01).
    filter {}

    delete_marker_replication {
      status = "Disabled"
    }

    destination {
      bucket        = local.backup_bucket_arn
      account       = var.backup_account_id
      storage_class = "STANDARD"
    }
  }

  # Replication needs versioning on the source (module.s3) first.
  depends_on = [module.s3, aws_iam_role_policy.backup_replication]
}
