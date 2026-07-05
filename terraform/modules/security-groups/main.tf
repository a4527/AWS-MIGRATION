resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb"
  description = "Allows public HTTP and HTTPS traffic to the load balancer."
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP from the internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS from the internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-alb"
  })
}

resource "aws_security_group" "application" {
  name        = "${var.name_prefix}-application"
  description = "Allows application runtime traffic from ALB and outbound dependencies."
  vpc_id      = var.vpc_id

  ingress {
    description     = "Application HTTP from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-application"
  })
}

resource "aws_security_group" "aurora" {
  name        = "${var.name_prefix}-aurora"
  description = "Allows PostgreSQL traffic from the application runtime."
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from application"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.application.id]
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-aurora"
  })
}

resource "aws_security_group" "redis" {
  name        = "${var.name_prefix}-redis"
  description = "Allows Redis traffic from the application runtime."
  vpc_id      = var.vpc_id

  ingress {
    description     = "Redis from application"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.application.id]
  }

  tags = merge(var.tags, {
    Name = "${var.name_prefix}-redis"
  })
}
