# ARCH-01: S3 gateway endpoint. Without it, S3 uploads/downloads from the
# backend and ECR image layers (served from S3) go through the NAT gateway and
# pay its $0.045/GB processing charge. A gateway endpoint has no hourly or data
# charge. Other AWS APIs (ECR API, Logs, SSM, SQS) still use the NAT route.
resource "aws_vpc_endpoint" "s3" {
  count = var.s3_gateway_endpoint_enabled ? 1 : 0

  vpc_id            = data.aws_vpc.this.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [data.aws_route_table.private.id]

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-s3-gateway"
  })
}
