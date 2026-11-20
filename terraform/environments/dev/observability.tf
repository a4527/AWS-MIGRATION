# OBS-01: one dashboard for the request path (ALB -> ECS -> Aurora/Redis) and
# the S3 post-processing Lambda, plus saved Logs Insights queries for per-API
# SLIs from the backend access log (event = http_request).

locals {
  alb_dim        = module.ecs.alb_arn_suffix
  tg_dim         = module.ecs.target_group_arn_suffix
  ecs_cluster    = module.ecs.cluster_name
  ecs_service    = module.ecs.service_name
  aurora_cluster = data.aws_rds_cluster.this.cluster_identifier
  redis_group    = "${local.name_prefix}-redis"
  lambda_name    = module.file_processor.function_name
  backend_logs   = module.ecs.log_group_name
  alarm_arn_base = "arn:aws:cloudwatch:${var.aws_region}:${data.aws_caller_identity.current.account_id}:alarm:${local.name_prefix}"

  # Per-route request count, success rate (non-5xx), and latency. Health checks excluded.
  sli_query = <<-EOT
    fields @timestamp, route, status, durationMs
    | filter event = "http_request" and route != "/api/health"
    | stats count(*) as requests,
            sum(status < 500) * 100.0 / count(*) as success_pct,
            pct(durationMs, 50) as p50_ms,
            pct(durationMs, 95) as p95_ms
      by method, route
    | sort requests desc
  EOT

  request_trace_query = <<-EOT
    fields @timestamp, log.level, message, requestId
    | filter requestId = "REPLACE_WITH_REQUEST_ID"
    | sort @timestamp asc
  EOT
}

resource "aws_cloudwatch_dashboard" "service" {
  dashboard_name = "${local.name_prefix}-service"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "text", x = 0, y = 0, width = 24, height = 2
        properties = {
          markdown = "## ${local.name_prefix} service (OBS-01)\nALB → ECS backend → Aurora / Redis, S3 → Lambda. API SLI는 아래 로그 위젯(경로별 성공률·p95)과 ALB 지표로 본다. 정의: docs/design/VALIDATION.md 11장"
        }
      },
      {
        type = "metric", x = 0, y = 2, width = 8, height = 6
        properties = {
          title  = "ALB requests and errors"
          region = var.aws_region
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", local.alb_dim],
            [".", "HTTPCode_Target_5XX_Count", ".", "."],
            [".", "HTTPCode_ELB_5XX_Count", ".", "."],
            [".", "HTTPCode_Target_4XX_Count", ".", "."],
          ]
        }
      },
      {
        type = "metric", x = 8, y = 2, width = 8, height = 6
        properties = {
          title  = "Target response time (s)"
          region = var.aws_region
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", local.alb_dim, { stat = "p50" }],
            ["...", { stat = "p95" }],
            ["...", { stat = "p99" }],
          ]
        }
      },
      {
        type = "metric", x = 16, y = 2, width = 8, height = 6
        properties = {
          title  = "Target health"
          region = var.aws_region
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", local.tg_dim, "LoadBalancer", local.alb_dim, { stat = "Minimum" }],
            [".", "UnHealthyHostCount", ".", ".", ".", ".", { stat = "Maximum" }],
          ]
        }
      },
      {
        type = "metric", x = 0, y = 8, width = 8, height = 6
        properties = {
          title  = "ECS CPU / memory (%)"
          region = var.aws_region
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", local.ecs_cluster, "ServiceName", local.ecs_service],
            [".", "MemoryUtilization", ".", ".", ".", "."],
          ]
        }
      },
      {
        type = "metric", x = 8, y = 8, width = 8, height = 6
        properties = {
          title  = "ECS running vs desired tasks"
          region = var.aws_region
          stat   = "Average"
          period = 60
          metrics = [
            ["ECS/ContainerInsights", "RunningTaskCount", "ClusterName", local.ecs_cluster, "ServiceName", local.ecs_service],
            [".", "DesiredTaskCount", ".", ".", ".", "."],
          ]
        }
      },
      {
        type = "metric", x = 16, y = 8, width = 8, height = 6
        properties = {
          title  = "Aurora capacity (ACU) and connections"
          region = var.aws_region
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/RDS", "ServerlessDatabaseCapacity", "DBClusterIdentifier", local.aurora_cluster],
            [".", "DatabaseConnections", ".", ".", { yAxis = "right" }],
          ]
        }
      },
      {
        type = "metric", x = 0, y = 14, width = 8, height = 6
        properties = {
          title  = "Redis hits / misses / connections"
          region = var.aws_region
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/ElastiCache", "CacheHits", "ReplicationGroupId", local.redis_group],
            [".", "CacheMisses", ".", "."],
            [".", "CurrConnections", ".", ".", { stat = "Average", yAxis = "right" }],
          ]
        }
      },
      {
        type = "metric", x = 8, y = 14, width = 8, height = 6
        properties = {
          title  = "File processor Lambda"
          region = var.aws_region
          period = 60
          metrics = [
            ["AWS/Lambda", "Invocations", "FunctionName", local.lambda_name, { stat = "Sum" }],
            [".", "Errors", ".", ".", { stat = "Sum" }],
            [".", "Duration", ".", ".", { stat = "p95", yAxis = "right" }],
          ]
        }
      },
      {
        type = "alarm", x = 16, y = 14, width = 8, height = 6
        properties = {
          title = "Alarms"
          alarms = [for name in [
            "alb-elb-5xx", "alb-no-healthy-target", "alb-target-5xx",
            "backend-cpu-high", "backend-memory-high",
            "file-processor-errors", "file-processor-duration",
          ] : "${local.alarm_arn_base}-${name}"]
        }
      },
      {
        type = "log", x = 0, y = 20, width = 24, height = 7
        properties = {
          title  = "API SLI by route (success = non-5xx, latency ms, health excluded)"
          region = var.aws_region
          view   = "table"
          query  = "SOURCE '${local.backend_logs}' | ${join(" ", [for line in split("\n", trimspace(local.sli_query)) : trimspace(line)])}"
        }
      },
    ]
  })

  depends_on = [module.ecs, module.file_processor]
}

resource "aws_cloudwatch_query_definition" "api_sli" {
  name            = "${local.name_prefix}/api-sli-by-route"
  log_group_names = [local.backend_logs]
  query_string    = local.sli_query
}

resource "aws_cloudwatch_query_definition" "request_trace" {
  name            = "${local.name_prefix}/request-trace"
  log_group_names = [local.backend_logs]
  query_string    = local.request_trace_query
}
