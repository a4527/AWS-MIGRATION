data "aws_iam_policy_document" "application_files" {
  statement {
    sid = "AllowFileBucketObjectAccess"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]
    resources = ["${var.file_bucket_arn}/*"]
  }

  statement {
    sid       = "AllowFileBucketList"
    actions   = ["s3:ListBucket"]
    resources = [var.file_bucket_arn]
  }
}

resource "aws_iam_policy" "application_files" {
  name        = "${var.name_prefix}-application-files"
  description = "Allows the application to access uploaded file objects."
  policy      = data.aws_iam_policy_document.application_files.json

  tags = var.tags
}
