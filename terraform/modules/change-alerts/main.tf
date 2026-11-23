# SEC-03: report security group rule and route changes that Terraform did not
# make. Drift detection cannot see them because no Terraform resource in the
# persistent layer owns these rules (TROUBLESHOOTING: "drift 검사가 별도 리소스로
# 붙인 보안 그룹 규칙을 잡지 못함"). EventBridge only receives CloudTrail API
# events while a trail is logging, so this module also creates the trail.

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
data "aws_partition" "current" {}

locals {
  account_id  = data.aws_caller_identity.current.account_id
  trail_name  = "${var.name_prefix}-trail"
  bucket_name = "${var.name_prefix}-cloudtrail-${local.account_id}"
  # Built from the name so the bucket policy does not depend on the trail.
  trail_arn = "arn:${data.aws_partition.current.partition}:cloudtrail:${data.aws_region.current.name}:${local.account_id}:trail/${local.trail_name}"

  # Role sessions appear as arn:aws:sts::<account>:assumed-role/<role name>/<session>.
  excluded_session_prefix = "arn:${data.aws_partition.current.partition}:sts::${local.account_id}:assumed-role/${element(split("/", var.excluded_role_arn), length(split("/", var.excluded_role_arn)) - 1)}/"
}

# --- trail log bucket ---------------------------------------------------------
resource "aws_s3_bucket" "trail" {
  bucket        = local.bucket_name
  force_destroy = var.force_destroy

  tags = merge(var.tags, { Name = local.bucket_name })
}

resource "aws_s3_bucket_public_access_block" "trail" {
  bucket = aws_s3_bucket.trail.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "trail" {
  bucket = aws_s3_bucket.trail.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "trail" {
  bucket = aws_s3_bucket.trail.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# The log files are only an audit copy for the alert; EventBridge already has
# the event. CloudTrail event history keeps 90 days of management events anyway.
resource "aws_s3_bucket_lifecycle_configuration" "trail" {
  bucket = aws_s3_bucket.trail.id

  rule {
    id     = "expire-trail-logs"
    status = "Enabled"

    filter {}

    expiration {
      days = var.log_retention_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

data "aws_iam_policy_document" "trail_bucket" {
  statement {
    sid       = "CloudTrailAclCheck"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.trail.arn]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.trail_arn]
    }
  }

  statement {
    sid       = "CloudTrailWrite"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.trail.arn}/AWSLogs/${local.account_id}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.trail_arn]
    }
  }

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.trail.arn, "${aws_s3_bucket.trail.arn}/*"]
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

resource "aws_s3_bucket_policy" "trail" {
  bucket = aws_s3_bucket.trail.id
  policy = data.aws_iam_policy_document.trail_bucket.json

  depends_on = [aws_s3_bucket_public_access_block.trail]
}

# --- trail ----------------------------------------------------------------------
# Single region, write management events only: the watched resources live in
# this region and only changes matter. The first copy of management events is
# free; the cost is the S3 storage and PUTs of the log files.
resource "aws_cloudtrail" "this" {
  name                          = local.trail_name
  s3_bucket_name                = aws_s3_bucket.trail.id
  is_multi_region_trail         = false
  include_global_service_events = false
  enable_log_file_validation    = true

  event_selector {
    read_write_type           = "WriteOnly"
    include_management_events = true
  }

  tags = var.tags

  depends_on = [aws_s3_bucket_policy.trail]
}

# --- rules ------------------------------------------------------------------------
resource "aws_cloudwatch_event_rule" "network_change" {
  name        = "${var.name_prefix}-network-change"
  description = "SEC-03: security group rule or route change on watched resources by anyone but the Terraform apply role."

  event_pattern = jsonencode({
    source        = ["aws.ec2"]
    "detail-type" = ["AWS API Call via CloudTrail"]
    detail = {
      eventSource  = ["ec2.amazonaws.com"]
      userIdentity = { arn = [{ "anything-but" = { prefix = local.excluded_session_prefix } }] }
      "$or" = [
        {
          eventName         = ["AuthorizeSecurityGroupIngress", "AuthorizeSecurityGroupEgress", "RevokeSecurityGroupIngress", "RevokeSecurityGroupEgress", "UpdateSecurityGroupRuleDescriptionsIngress", "UpdateSecurityGroupRuleDescriptionsEgress"]
          requestParameters = { groupId = var.security_group_ids }
        },
        {
          eventName         = ["ModifySecurityGroupRules"]
          requestParameters = { ModifySecurityGroupRulesRequest = { GroupId = var.security_group_ids } }
        },
        {
          eventName         = ["CreateRoute", "ReplaceRoute", "DeleteRoute"]
          requestParameters = { routeTableId = var.route_table_ids }
        },
      ]
    }
  })

  tags = var.tags
}

resource "aws_cloudwatch_event_target" "network_change" {
  rule = aws_cloudwatch_event_rule.network_change.name
  arn  = var.topic_arn

  input_transformer {
    input_paths = {
      event = "$.detail.eventName"
      who   = "$.detail.userIdentity.arn"
      time  = "$.detail.eventTime"
      ip    = "$.detail.sourceIPAddress"
      agent = "$.detail.userAgent"
      error = "$.detail.errorCode"
      sg    = "$.detail.requestParameters.groupId"
      rt    = "$.detail.requestParameters.routeTableId"
      id    = "$.detail.eventID"
    }
    input_template = "\"[${var.name_prefix}] Network change outside Terraform CI: <event> at <time> by <who> from <ip> (agent <agent>). security group <sg> route table <rt> error <error>. CloudTrail event ID <id>. If unintended, revert it and check who made it (docs/TROUBLESHOOTING.md).\""
  }
}

# Turning the watch off must not be silent either.
resource "aws_cloudwatch_event_rule" "watch_tampering" {
  name        = "${var.name_prefix}-change-watch-tampering"
  description = "SEC-03: the trail or the change rule was stopped, changed, or deleted."

  event_pattern = jsonencode({
    "detail-type" = ["AWS API Call via CloudTrail"]
    detail = {
      "$or" = [
        {
          eventSource       = ["cloudtrail.amazonaws.com"]
          eventName         = ["StopLogging", "DeleteTrail", "UpdateTrail", "PutEventSelectors", "PutAdvancedEventSelectors"]
          requestParameters = { name = [local.trail_name, local.trail_arn] }
        },
        {
          eventSource       = ["events.amazonaws.com"]
          eventName         = ["DisableRule", "DeleteRule", "PutRule"]
          requestParameters = { name = [aws_cloudwatch_event_rule.network_change.name] }
        },
        {
          eventSource       = ["events.amazonaws.com"]
          eventName         = ["RemoveTargets", "PutTargets"]
          requestParameters = { rule = [aws_cloudwatch_event_rule.network_change.name] }
        },
      ]
    }
  })

  tags = var.tags
}

resource "aws_cloudwatch_event_target" "watch_tampering" {
  rule = aws_cloudwatch_event_rule.watch_tampering.name
  arn  = var.topic_arn

  input_transformer {
    input_paths = {
      event = "$.detail.eventName"
      who   = "$.detail.userIdentity.arn"
      time  = "$.detail.eventTime"
      error = "$.detail.errorCode"
    }
    input_template = "\"[${var.name_prefix}] Change watch modified: <event> at <time> by <who> (error <error>). Expected only during a terraform apply of the persistent layer.\""
  }
}
