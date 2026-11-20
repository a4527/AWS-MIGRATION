# FILE-03 (DEC-018): real malware scanning with GuardDuty Malware Protection
# for S3. GuardDuty scans every new object under files/, publishes the result
# to EventBridge, a rule forwards it to SQS, and the backend applies it to the
# database and cache. Upload stores SCANNING/PENDING; only CLEAN downloads.

locals {
  scan_bucket_arn = data.aws_s3_bucket.files.arn
  scan_prefix     = var.file_processor_object_prefix
}

# --- GuardDuty service role (least privilege, files/ prefix only) ----------
data "aws_iam_policy_document" "guardduty_scan_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["malware-protection-plan.guardduty.amazonaws.com"]
    }
    # Confused-deputy protection: only this account's GuardDuty may assume it.
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "guardduty_scan" {
  name               = "${local.name_prefix}-guardduty-s3-scan"
  assume_role_policy = data.aws_iam_policy_document.guardduty_scan_assume.json

  tags = local.common_tags
}

data "aws_iam_policy_document" "guardduty_scan" {
  statement {
    sid       = "ManagedRuleForS3Events"
    actions   = ["events:PutRule", "events:DeleteRule", "events:PutTargets", "events:RemoveTargets"]
    resources = ["arn:aws:events:${var.aws_region}:${data.aws_caller_identity.current.account_id}:rule/DO-NOT-DELETE-AmazonGuardDutyMalwareProtectionS3*"]
    condition {
      test     = "StringLike"
      variable = "events:ManagedBy"
      values   = ["malware-protection-plan.guardduty.amazonaws.com"]
    }
  }

  statement {
    sid       = "MonitorManagedRule"
    actions   = ["events:DescribeRule", "events:ListTargetsByRule"]
    resources = ["arn:aws:events:${var.aws_region}:${data.aws_caller_identity.current.account_id}:rule/DO-NOT-DELETE-AmazonGuardDutyMalwareProtectionS3*"]
  }

  statement {
    sid       = "ScanAndTagUploadedObjects"
    actions   = ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObjectTagging", "s3:GetObjectTagging", "s3:PutObjectVersionTagging", "s3:GetObjectVersionTagging"]
    resources = ["${local.scan_bucket_arn}/${local.scan_prefix}*"]
  }

  statement {
    sid       = "BucketEventBridgeAndOwnership"
    actions   = ["s3:PutBucketNotification", "s3:GetBucketNotification", "s3:ListBucket"]
    resources = [local.scan_bucket_arn]
  }

  statement {
    sid       = "ValidationObject"
    actions   = ["s3:PutObject"]
    resources = ["${local.scan_bucket_arn}/malware-protection-resource-validation-object"]
  }
}

resource "aws_iam_role_policy" "guardduty_scan" {
  name   = "${local.name_prefix}-guardduty-s3-scan"
  role   = aws_iam_role.guardduty_scan.id
  policy = data.aws_iam_policy_document.guardduty_scan.json
}

resource "aws_guardduty_malware_protection_plan" "files" {
  role = aws_iam_role.guardduty_scan.arn

  protected_resource {
    s3_bucket {
      bucket_name     = data.aws_s3_bucket.files.bucket
      object_prefixes = [local.scan_prefix]
    }
  }

  # GuardDutyMalwareScanStatus tag on each object, for console visibility.
  # The verdict the application trusts is the one stored in the database.
  actions {
    tagging {
      status = "ENABLED"
    }
  }

  tags = local.common_tags

  depends_on = [aws_iam_role_policy.guardduty_scan, module.file_processor]
}

# --- Result delivery: EventBridge -> SQS (+ DLQ) -----------------------------
resource "aws_sqs_queue" "scan_results_dlq" {
  name                      = "${local.name_prefix}-scan-results-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true

  tags = local.common_tags
}

resource "aws_sqs_queue" "scan_results" {
  name                       = "${local.name_prefix}-scan-results"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600
  receive_wait_time_seconds  = 10
  sqs_managed_sse_enabled    = true

  # A result whose file row does not exist yet is retried; after 10 receives
  # (about 5 minutes) it moves to the DLQ and the DLQ alarm fires.
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.scan_results_dlq.arn
    maxReceiveCount     = 10
  })

  tags = local.common_tags
}

resource "aws_cloudwatch_event_rule" "scan_results" {
  name        = "${local.name_prefix}-guardduty-scan-results"
  description = "GuardDuty Malware Protection scan results for the file bucket."

  event_pattern = jsonencode({
    source        = ["aws.guardduty"]
    "detail-type" = ["GuardDuty Malware Protection Object Scan Result"]
    detail = {
      s3ObjectDetails = {
        bucketName = [data.aws_s3_bucket.files.bucket]
      }
    }
  })

  tags = local.common_tags
}

resource "aws_cloudwatch_event_target" "scan_results" {
  rule = aws_cloudwatch_event_rule.scan_results.name
  arn  = aws_sqs_queue.scan_results.arn
}

data "aws_iam_policy_document" "scan_results_queue" {
  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.scan_results.arn]
    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_cloudwatch_event_rule.scan_results.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "scan_results" {
  queue_url = aws_sqs_queue.scan_results.id
  policy    = data.aws_iam_policy_document.scan_results_queue.json
}

# --- Backend may only consume this queue -------------------------------------
data "aws_iam_policy_document" "scan_results_consumer" {
  statement {
    actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.scan_results.arn]
  }
}

resource "aws_iam_policy" "scan_results_consumer" {
  name        = "${local.name_prefix}-scan-results-consumer"
  description = "Backend reads GuardDuty scan results from SQS (FILE-03)."
  policy      = data.aws_iam_policy_document.scan_results_consumer.json

  tags = local.common_tags
}

# --- Undeliverable results need attention (OBS-02) ---------------------------
resource "aws_cloudwatch_metric_alarm" "scan_results_dlq" {
  alarm_name          = "${local.name_prefix}-scan-results-dlq"
  alarm_description   = "Scan results could not be applied (file row missing, malformed event). Files stay PENDING until the timeout job quarantines them."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [data.aws_sns_topic.alarms.arn]
  ok_actions          = [data.aws_sns_topic.alarms.arn]

  dimensions = {
    QueueName = aws_sqs_queue.scan_results_dlq.name
  }

  tags = local.common_tags
}
