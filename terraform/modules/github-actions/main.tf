resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = var.github_oidc_thumbprints

  tags = var.tags
}

data "aws_iam_policy_document" "deploy_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_repository}:ref:refs/heads/${var.github_branch}",
      ]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name               = "${var.name_prefix}-github-actions-deploy"
  assume_role_policy = data.aws_iam_policy_document.deploy_assume_role.json

  tags = var.tags
}

data "aws_iam_policy_document" "deploy" {
  statement {
    sid = "AllowEcrAuthorization"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    sid = "AllowBackendImagePush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      # Docker reads the manifest back after a push (HEAD /manifests); without
      # these the push ends with 403 (2026-11-20 run 44664880316).
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [var.ecr_repository_arn]
  }

  statement {
    sid = "AllowEcsRead"
    actions = [
      "ecs:DescribeClusters",
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
    ]
    resources = ["*"]
  }

  statement {
    sid = "AllowEcsDeploy"
    actions = [
      "ecs:UpdateService",
    ]
    resources = [var.ecs_service_arn]

    condition {
      test     = "ArnEquals"
      variable = "ecs:cluster"
      values   = [var.ecs_cluster_arn]
    }
  }

  statement {
    sid = "AllowRegisterTaskDefinition"
    actions = [
      "ecs:RegisterTaskDefinition",
    ]
    resources = ["*"]
  }

  # CD copies the Owner tag onto each new revision; tagging is allowed only at registration.
  statement {
    sid = "AllowTagTaskDefinitionOnRegister"
    actions = [
      "ecs:TagResource",
    ]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "ecs:CreateAction"
      values   = ["RegisterTaskDefinition"]
    }
  }

  # DEC-010: listing the writer's log files resumes a paused Aurora instance
  # before the first task starts. Read-only; no connection or data access.
  statement {
    sid = "AllowAuroraWake"
    actions = [
      "rds:DescribeDBClusters",
      "rds:DescribeDBLogFiles",
    ]
    resources = [
      var.aurora_cluster_arn,
      var.aurora_instance_arn_pattern,
    ]
  }

  statement {
    sid       = "AllowAuroraEvents"
    actions   = ["rds:DescribeEvents"]
    resources = ["*"]
  }

  statement {
    sid = "AllowPassEcsTaskRoles"
    actions = [
      "iam:PassRole",
    ]
    resources = [
      var.ecs_task_role_arn,
      var.ecs_execution_role_arn,
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_policy" "deploy" {
  name        = "${var.name_prefix}-github-actions-deploy"
  description = "Allows GitHub Actions to push the backend image and deploy the ECS service."
  policy      = data.aws_iam_policy_document.deploy.json

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "deploy" {
  role       = aws_iam_role.deploy.name
  policy_arn = aws_iam_policy.deploy.arn
}
