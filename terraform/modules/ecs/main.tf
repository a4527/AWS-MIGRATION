data "aws_iam_policy_document" "ecs_task_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.name_prefix}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# SEC-01: only these two parameters. Both use the AWS managed aws/ssm key, whose
# key policy already allows decryption through SSM, so no kms:Decrypt is added.
data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid     = "ReadApplicationSecrets"
    actions = ["ssm:GetParameters"]
    resources = [
      var.database_password_parameter_arn,
      var.app_jwt_secret_parameter_arn,
    ]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "${var.name_prefix}-ecs-execution-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "task_application" {
  count = length(var.app_policy_arns)

  role       = aws_iam_role.task.name
  policy_arn = var.app_policy_arns[count.index]
}

resource "aws_cloudwatch_log_group" "application" {
  name              = "/ecs/${var.name_prefix}-backend"
  retention_in_days = var.log_retention_in_days

  tags = var.tags
}

resource "aws_ecs_cluster" "this" {
  name = "${var.name_prefix}-ecs"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = var.tags
}

resource "aws_lb" "application" {
  name               = "${var.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.alb_security_group_id]
  subnets            = var.public_subnet_ids
  # Reject requests with malformed header names instead of forwarding them (IAC-03 scan).
  drop_invalid_header_fields = true

  tags = var.tags
}

resource "aws_lb_target_group" "application" {
  name        = "${var.name_prefix}-backend"
  port        = var.container_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  # ARCH-01: the 300 s default kept a stopped task's replacement waiting (11-24,
  # 6 min 44 s outage). Requests here finish within seconds.
  deregistration_delay = var.deregistration_delay_seconds

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = var.health_check_path
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = var.tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.application.arn
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = var.certificate_arn == null ? [1] : []

    content {
      type             = "forward"
      target_group_arn = aws_lb_target_group.application.arn
    }
  }

  dynamic "default_action" {
    for_each = var.certificate_arn == null ? [] : [1]

    content {
      type = "redirect"

      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }

  tags = var.tags
}

resource "aws_lb_listener" "https" {
  count = var.certificate_arn == null ? 0 : 1

  load_balancer_arn = aws_lb.application.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = var.certificate_arn
  ssl_policy        = var.ssl_policy

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.application.arn
  }

  tags = var.tags
}

resource "aws_ecs_task_definition" "application" {
  family                   = "${var.name_prefix}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = var.container_image
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          hostPort      = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = concat([
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "postgres,redis,s3"
        },
        {
          name  = "AWS_REGION"
          value = var.aws_region
        },
        {
          name  = "S3_BUCKET"
          value = var.s3_bucket_name
        },
        {
          name  = "S3_OBJECT_PREFIX"
          value = var.s3_object_prefix
        },
        {
          name  = "DB_URL"
          value = var.database_url
        },
        {
          name  = "DB_USERNAME"
          value = var.database_username
        },
        {
          name  = "REDIS_HOST"
          value = var.redis_host
        },
        {
          name  = "REDIS_PORT"
          value = "6379"
        },
        {
          name  = "REDIS_SSL_ENABLED"
          value = "true"
        },
        {
          # OBS-01: JSON logs with requestId and access-log fields as keys.
          name  = "LOGGING_STRUCTURED_FORMAT_CONSOLE"
          value = "ecs"
        }
      ], [for name, value in var.extra_environment : { name = name, value = value }])

      # SEC-01: resolved by the execution role when the task starts. The task
      # definition holds only parameter ARNs.
      secrets = [
        {
          name      = "DB_PASSWORD"
          valueFrom = var.database_password_parameter_arn
        },
        {
          name      = "APP_JWT_SECRET"
          valueFrom = var.app_jwt_secret_parameter_arn
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.application.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "backend"
        }
      }
    }
  ])

  tags = var.tags
}

resource "aws_ecs_service" "application" {
  name            = "${var.name_prefix}-backend"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.application.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"
  # The app needs about 60 s to start on 0.5 vCPU. Without a grace period ECS
  # killed staging tasks on ALB health failures before they were up (exit 143),
  # and the circuit breaker failed the deploy (2026-11-20).
  health_check_grace_period_seconds = var.health_check_grace_period_seconds

  # CICD-03: tasks that fail to start or fail health checks roll the service
  # back to the last completed deployment automatically.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Tasks inherit the service tags (including Owner).
  enable_ecs_managed_tags = true
  propagate_tags          = "SERVICE"

  lifecycle {
    # CD owns the deployed revision; Application Auto Scaling owns task count.
    # These configured values still apply when creating the service.
    ignore_changes = [task_definition, desired_count]
  }

  network_configuration {
    assign_public_ip = false
    security_groups  = [var.application_security_group_id]
    subnets          = var.private_subnet_ids
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.application.arn
    container_name   = "backend"
    container_port   = var.container_port
  }

  depends_on = [
    aws_iam_role_policy_attachment.execution,
    aws_iam_role_policy.execution_secrets,
    aws_lb_listener.http,
    aws_lb_listener.https,
  ]

  tags = var.tags
}

resource "aws_appautoscaling_target" "application" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.application.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name_prefix}-backend-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.application.resource_id
  scalable_dimension = aws_appautoscaling_target.application.scalable_dimension
  service_namespace  = aws_appautoscaling_target.application.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }

    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    target_value       = var.cpu_target_value
  }
}

resource "aws_appautoscaling_policy" "memory" {
  name               = "${var.name_prefix}-backend-memory"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.application.resource_id
  scalable_dimension = aws_appautoscaling_target.application.scalable_dimension
  service_namespace  = aws_appautoscaling_target.application.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }

    # The JVM keeps its heap after load, so memory stays high and this policy
    # would block every scale-in (PERF-01, 2026-11-24). Memory may still scale
    # out; scale-in is decided by the CPU policy alone.
    disable_scale_in   = true
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    target_value       = var.memory_target_value
  }
}

resource "aws_cloudwatch_metric_alarm" "service_cpu_high" {
  alarm_name          = "${var.name_prefix}-backend-cpu-high"
  alarm_description   = "Backend ECS service average CPU utilization is high."
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.cpu_target_value
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_action_arns
  ok_actions          = var.alarm_action_arns

  dimensions = {
    ClusterName = aws_ecs_cluster.this.name
    ServiceName = aws_ecs_service.application.name
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "service_memory_high" {
  alarm_name          = "${var.name_prefix}-backend-memory-high"
  alarm_description   = "Backend ECS service average memory utilization is high."
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.memory_target_value
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_action_arns
  ok_actions          = var.alarm_action_arns

  dimensions = {
    ClusterName = aws_ecs_cluster.this.name
    ServiceName = aws_ecs_service.application.name
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "target_5xx" {
  alarm_name        = "${var.name_prefix}-alb-target-5xx"
  alarm_description = "Backend (target) returned 5xx responses."
  namespace         = "AWS/ApplicationELB"
  metric_name       = "HTTPCode_Target_5XX_Count"
  statistic         = "Sum"
  period            = 60
  # Metrics arrive 1-2 minutes late; alarm if any of the last 3 minutes breached
  # (a short burst was missed with a single period, OPS-01 T3).
  evaluation_periods  = 3
  datapoints_to_alarm = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_action_arns
  ok_actions          = var.alarm_action_arns

  dimensions = {
    LoadBalancer = aws_lb.application.arn_suffix
    TargetGroup  = aws_lb_target_group.application.arn_suffix
  }

  tags = var.tags
}

# OBS-02: the ALB itself answers 502/503 when no healthy target exists (task
# stopped, failed deployment). Target 5xx alone misses that (V-AWS-04).
resource "aws_cloudwatch_metric_alarm" "elb_5xx" {
  alarm_name        = "${var.name_prefix}-alb-elb-5xx"
  alarm_description = "ALB returned 5xx itself (no healthy target, timeout, or connection error)."
  namespace         = "AWS/ApplicationELB"
  metric_name       = "HTTPCode_ELB_5XX_Count"
  statistic         = "Sum"
  period            = 60
  # Metrics arrive 1-2 minutes late; alarm if any of the last 3 minutes breached
  # (a short burst was missed with a single period, OPS-01 T3).
  evaluation_periods  = 3
  datapoints_to_alarm = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_action_arns
  ok_actions          = var.alarm_action_arns

  dimensions = {
    LoadBalancer = aws_lb.application.arn_suffix
  }

  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "no_healthy_target" {
  alarm_name          = "${var.name_prefix}-alb-no-healthy-target"
  alarm_description   = "Backend target group has no healthy target for 2 minutes."
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = var.alarm_action_arns
  ok_actions          = var.alarm_action_arns

  # ALB stops publishing HealthyHostCount when no target is registered, so a
  # total outage left gaps and the plain alarm stayed OK (OPS-01 T5). FILL
  # turns those gaps into 0.
  metric_query {
    id          = "healthy"
    expression  = "FILL(raw, 0)"
    label       = "HealthyHostCount (missing = 0)"
    return_data = true
  }

  metric_query {
    id = "raw"

    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "HealthyHostCount"
      stat        = "Minimum"
      period      = 60

      dimensions = {
        LoadBalancer = aws_lb.application.arn_suffix
        TargetGroup  = aws_lb_target_group.application.arn_suffix
      }
    }
  }

  tags = var.tags
}

# CICD-02 (DEC-017): staging service in the same cluster. CD deploys a new
# revision here first, runs smoke tests through the ALB with the
# X-Deploy-Stage header, and promotes the same revision to the main service.
locals {
  routing_listener_arn = var.certificate_arn == null ? aws_lb_listener.http.arn : aws_lb_listener.https[0].arn
}

resource "aws_lb_target_group" "staging" {
  count = var.staging_enabled ? 1 : 0

  name        = "${var.name_prefix}-backend-stg"
  port        = var.container_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id
  # Staging tasks are replaced on every deploy; do not hold them for 5 minutes.
  deregistration_delay = 30

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 15
    matcher             = "200"
    path                = var.health_check_path
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = var.tags
}

# Not a security boundary: anyone who sends the header reaches staging. It
# shares the data layer with the main service (lab cost trade-off, DEC-017).
resource "aws_lb_listener_rule" "staging" {
  count = var.staging_enabled ? 1 : 0

  listener_arn = local.routing_listener_arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.staging[0].arn
  }

  condition {
    http_header {
      http_header_name = "X-Deploy-Stage"
      values           = [var.staging_header_value]
    }
  }

  tags = var.tags
}

resource "aws_ecs_service" "staging" {
  count = var.staging_enabled ? 1 : 0

  name            = "${var.name_prefix}-backend-staging"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.application.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  health_check_grace_period_seconds = var.health_check_grace_period_seconds

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  enable_ecs_managed_tags = true
  propagate_tags          = "SERVICE"

  lifecycle {
    ignore_changes = [task_definition]
  }

  network_configuration {
    assign_public_ip = false
    security_groups  = [var.application_security_group_id]
    subnets          = var.private_subnet_ids
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.staging[0].arn
    container_name   = "backend"
    container_port   = var.container_port
  }

  depends_on = [aws_lb_listener_rule.staging]

  tags = var.tags
}
