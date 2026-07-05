locals {
  name_prefix = "${var.project_name}-${var.environment}"

  common_tags = merge(var.tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  })
}

module "vpc" {
  source = "../../modules/vpc"

  name_prefix        = local.name_prefix
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
  public_subnets     = var.public_subnets
  private_subnets    = var.private_subnets
  database_subnets   = var.database_subnets
  tags               = local.common_tags
}

module "ecr" {
  source = "../../modules/ecr"

  name_prefix = local.name_prefix
  repositories = {
    backend = {
      image_tag_mutability = "MUTABLE"
      scan_on_push         = true
    }
  }
  tags = local.common_tags
}

module "s3" {
  source = "../../modules/s3"

  bucket_name   = var.file_bucket_name
  force_destroy = var.force_destroy_buckets
  tags          = local.common_tags
}

module "iam" {
  source = "../../modules/iam"

  name_prefix     = local.name_prefix
  file_bucket_arn = module.s3.bucket_arn
  tags            = local.common_tags
}

module "security_groups" {
  source = "../../modules/security-groups"

  name_prefix = local.name_prefix
  vpc_id      = module.vpc.vpc_id
  tags        = local.common_tags
}

module "ecs" {
  source = "../../modules/ecs"

  name_prefix                   = local.name_prefix
  vpc_id                        = module.vpc.vpc_id
  public_subnet_ids             = module.vpc.public_subnet_ids
  private_subnet_ids            = module.vpc.private_subnet_ids
  alb_security_group_id         = module.security_groups.alb_security_group_id
  application_security_group_id = module.security_groups.application_security_group_id
  container_image               = "${module.ecr.repository_urls["backend"]}:${var.ecs_backend_image_tag}"
  container_port                = var.ecs_container_port
  cpu                           = var.ecs_task_cpu
  memory                        = var.ecs_task_memory
  desired_count                 = var.ecs_desired_count
  health_check_path             = var.ecs_health_check_path
  aws_region                    = var.aws_region
  s3_bucket_name                = module.s3.bucket_name
  database_url                  = "jdbc:postgresql://${module.aurora.cluster_endpoint}:5432/${var.aurora_database_name}"
  database_username             = var.aurora_master_username
  database_password             = var.aurora_master_password
  redis_host                    = module.elasticache.primary_endpoint_address
  app_jwt_secret                = var.app_jwt_secret
  app_policy_arns               = [module.iam.application_files_policy_arn]
  tags                          = local.common_tags
}

module "github_actions" {
  source = "../../modules/github-actions"

  name_prefix             = local.name_prefix
  github_repository       = var.github_repository
  github_branch           = var.github_branch
  github_oidc_thumbprints = var.github_oidc_thumbprints
  ecr_repository_arn      = module.ecr.repository_arns["backend"]
  ecs_cluster_arn         = module.ecs.cluster_arn
  ecs_service_arn         = module.ecs.service_arn
  ecs_task_role_arn       = module.ecs.task_role_arn
  ecs_execution_role_arn  = module.ecs.execution_role_arn
  tags                    = local.common_tags
}

module "aurora" {
  source = "../../modules/aurora"

  name_prefix         = local.name_prefix
  database_subnet_ids = module.vpc.database_subnet_ids
  security_group_ids  = [module.security_groups.aurora_security_group_id]
  database_name       = var.aurora_database_name
  master_username     = var.aurora_master_username
  master_password     = var.aurora_master_password
  engine_version      = var.aurora_engine_version
  instance_count      = var.aurora_instance_count
  min_capacity        = var.aurora_min_capacity
  max_capacity        = var.aurora_max_capacity
  skip_final_snapshot = var.aurora_skip_final_snapshot
  tags                = local.common_tags
}

module "elasticache" {
  source = "../../modules/elasticache"

  name_prefix         = local.name_prefix
  database_subnet_ids = module.vpc.database_subnet_ids
  security_group_ids  = [module.security_groups.redis_security_group_id]
  node_type           = var.redis_node_type
  num_cache_clusters  = var.redis_num_cache_clusters
  tags                = local.common_tags
}
