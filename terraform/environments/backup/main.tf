locals {
  replication_role_arn = "arn:aws:iam::${var.source_account_id}:role/${var.replication_role_name}"
}

resource "aws_s3_bucket" "backup" {
  bucket = var.bucket_name

  tags = {
    Name = var.bucket_name
  }
}

# Replicas are owned by this account; ACLs are ignored.
resource "aws_s3_bucket_ownership_controls" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "backup" {
  bucket = aws_s3_bucket.backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# The dump holds user emails and password hashes (ADR-002).
resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Replication requires versioning on the destination.
resource "aws_s3_bucket_versioning" "backup" {
  bucket = aws_s3_bucket.backup.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    id     = "expire-overwritten-copies"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = var.noncurrent_version_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.backup]
}

data "aws_iam_policy_document" "backup" {
  # Only the bootcamp replication role may write, and only replicas.
  statement {
    sid       = "ReplicationRoleWritesCopies"
    actions   = ["s3:ReplicateObject", "s3:ReplicateTags"]
    resources = ["${aws_s3_bucket.backup.arn}/*"]
    principals {
      type        = "AWS"
      identifiers = [local.replication_role_arn]
    }
  }

  statement {
    sid       = "ReplicationRoleChecksVersioning"
    actions   = ["s3:GetBucketVersioning", "s3:PutBucketVersioning"]
    resources = [aws_s3_bucket.backup.arn]
    principals {
      type        = "AWS"
      identifiers = [local.replication_role_arn]
    }
  }

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.backup.arn, "${aws_s3_bucket.backup.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

# The principal must exist: apply ../dev-data (replication role) before this.
resource "aws_s3_bucket_policy" "backup" {
  bucket = aws_s3_bucket.backup.id
  policy = data.aws_iam_policy_document.backup.json

  depends_on = [aws_s3_bucket_public_access_block.backup]
}
