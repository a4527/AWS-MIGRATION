# IAC-03: GitHub Actions roles for Terraform. Read (plan, drift) and write
# (apply) are separate roles with separate OIDC subjects.

data "aws_caller_identity" "current" {}

locals {
  state_bucket_arn = "arn:aws:s3:::${var.state_bucket_name}"
  account_id       = data.aws_caller_identity.current.account_id
}

# --- plan / drift: read only ------------------------------------------------
data "aws_iam_policy_document" "plan_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    # Pull requests of this repository (fork PRs get no OIDC token) and main
    # (scheduled drift detection).
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_repository}:pull_request",
        "repo:${var.github_repository}:ref:refs/heads/${var.github_branch}",
      ]
    }
  }
}

resource "aws_iam_role" "plan" {
  name                 = "${var.name_prefix}-terraform-plan"
  assume_role_policy   = data.aws_iam_policy_document.plan_assume.json
  max_session_duration = 3600

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "plan_read_only" {
  role       = aws_iam_role.plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

data "aws_iam_policy_document" "plan" {
  statement {
    sid       = "ReadState"
    actions   = ["s3:GetObject", "s3:ListBucket"]
    resources = [local.state_bucket_arn, "${local.state_bucket_arn}/*"]
  }

  # ReadOnlyAccess would also let a plan read uploaded user files and secret
  # values. Terraform does not need either.
  statement {
    sid       = "DenyUserDataAndSecretValues"
    effect    = "Deny"
    actions   = ["s3:GetObject", "s3:GetObjectVersion", "secretsmanager:GetSecretValue"]
    resources = ["arn:aws:s3:::${var.file_bucket_name}/*", "arn:aws:secretsmanager:*:${local.account_id}:secret:*"]
  }
}

resource "aws_iam_role_policy" "plan" {
  name   = "${var.name_prefix}-terraform-plan"
  role   = aws_iam_role.plan.id
  policy = data.aws_iam_policy_document.plan.json
}

# --- apply: only after approval in the GitHub environment -------------------
data "aws_iam_policy_document" "apply_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${var.apply_environment}"]
    }
  }
}

resource "aws_iam_role" "apply" {
  name                 = "${var.name_prefix}-terraform-apply"
  assume_role_policy   = data.aws_iam_policy_document.apply_assume.json
  max_session_duration = 3600

  tags = var.tags
}

# Broad by necessity: the lab layer spans ECS, ELB, ElastiCache, Lambda,
# CloudWatch, EventBridge, SQS, GuardDuty and more. IAM is limited below.
resource "aws_iam_role_policy_attachment" "apply_power_user" {
  role       = aws_iam_role.apply.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "apply" {
  statement {
    sid       = "WriteState"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
    resources = [local.state_bucket_arn, "${local.state_bucket_arn}/*"]
  }

  statement {
    sid = "ManageProjectIamOnly"
    actions = [
      "iam:CreateRole", "iam:DeleteRole", "iam:UpdateRole", "iam:UpdateAssumeRolePolicy", "iam:TagRole", "iam:UntagRole",
      "iam:PutRolePolicy", "iam:DeleteRolePolicy", "iam:AttachRolePolicy", "iam:DetachRolePolicy",
      "iam:CreatePolicy", "iam:DeletePolicy", "iam:CreatePolicyVersion", "iam:DeletePolicyVersion", "iam:TagPolicy", "iam:UntagPolicy",
      "iam:PassRole",
    ]
    resources = [
      "arn:aws:iam::${local.account_id}:role/${var.name_prefix}-*",
      "arn:aws:iam::${local.account_id}:policy/${var.name_prefix}-*",
    ]
  }

  statement {
    sid       = "ReadIam"
    actions   = ["iam:Get*", "iam:List*"]
    resources = ["*"]
  }

  statement {
    sid       = "ServiceLinkedRoles"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${local.account_id}:role/aws-service-role/*"]
  }

  # The apply role must not be able to widen its own or the plan role's trust.
  statement {
    sid       = "ProtectCiRoles"
    effect    = "Deny"
    actions   = ["iam:*"]
    resources = [aws_iam_role.plan.arn, "arn:aws:iam::${local.account_id}:role/${var.name_prefix}-terraform-apply"]
  }
}

resource "aws_iam_role_policy" "apply" {
  name   = "${var.name_prefix}-terraform-apply"
  role   = aws_iam_role.apply.id
  policy = data.aws_iam_policy_document.apply.json
}
