data "archive_file" "function" {
  type        = "zip"
  source_file = var.source_file_path
  output_path = "${path.root}/.terraform/${var.name_prefix}-file-processor.zip"
}

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "${var.name_prefix}-file-processor"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json

  tags = var.tags
}

data "aws_iam_policy_document" "this" {
  statement {
    sid = "AllowFileObjectReadAndTagging"
    actions = [
      "s3:GetObject",
      "s3:GetObjectTagging",
      "s3:PutObjectTagging",
    ]
    resources = ["${var.file_bucket_arn}/${var.object_prefix}*"]
  }

  statement {
    sid = "AllowFileBucketList"
    actions = [
      "s3:ListBucket",
    ]
    resources = [var.file_bucket_arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${var.object_prefix}*"]
    }
  }

  statement {
    sid = "AllowCloudWatchLogs"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.this.arn}:*"]
  }
}

resource "aws_iam_policy" "this" {
  name        = "${var.name_prefix}-file-processor"
  description = "Allows the file processor Lambda to read uploaded objects and write processing tags."
  policy      = data.aws_iam_policy_document.this.json

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "this" {
  role       = aws_iam_role.this.name
  policy_arn = aws_iam_policy.this.arn
}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/aws/lambda/${var.name_prefix}-file-processor"
  retention_in_days = var.log_retention_in_days

  tags = var.tags
}

resource "aws_lambda_function" "this" {
  function_name    = "${var.name_prefix}-file-processor"
  description      = "Processes S3 object-created events for uploaded files."
  role             = aws_iam_role.this.arn
  handler          = "handler.lambda_handler"
  runtime          = "python3.12"
  filename         = data.archive_file.function.output_path
  source_code_hash = data.archive_file.function.output_base64sha256
  timeout          = var.timeout_seconds
  memory_size      = var.memory_size

  environment {
    variables = {
      BLOCKED_EXTENSIONS = join(",", var.blocked_extensions)
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.this,
    aws_iam_role_policy_attachment.this,
  ]

  tags = var.tags
}

resource "aws_lambda_permission" "allow_s3" {
  statement_id  = "AllowExecutionFromS3"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.this.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = var.file_bucket_arn
}

resource "aws_s3_bucket_notification" "this" {
  bucket = var.file_bucket_id

  lambda_function {
    lambda_function_arn = aws_lambda_function.this.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = var.object_prefix
  }

  depends_on = [aws_lambda_permission.allow_s3]
}

resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.name_prefix}-file-processor-errors"
  alarm_description   = "File processor Lambda has one or more errors."
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.this.function_name
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "lambda_duration" {
  alarm_name          = "${var.name_prefix}-file-processor-duration"
  alarm_description   = "File processor Lambda p95 duration is above the configured threshold."
  namespace           = "AWS/Lambda"
  metric_name         = "Duration"
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.duration_alarm_threshold_ms
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.this.function_name
  }

  tags = var.tags
}
