output "nat_gateway_id" {
  description = "NAT gateway ID."
  value       = aws_nat_gateway.this.id
}
