# Terraform

AWS 인프라를 코드로 정의하는 영역입니다.

## 구조

```text
terraform/
├── environments/
│   └── dev/
│       ├── backend.tf.example
│       ├── main.tf
│       ├── outputs.tf
│       ├── providers.tf
│       ├── terraform.tfvars.example
│       └── variables.tf
└── modules/
    ├── aurora/
    ├── ecr/
    ├── ecs/
    ├── elasticache/
    ├── github-actions/
    ├── iam/
    ├── s3/
    ├── security-groups/
    └── vpc/
```

## 구성

- VPC
- Public subnet: ALB, NAT Gateway
- Private subnet: ECS Fargate task
- Database subnet: Aurora PostgreSQL, ElastiCache
- ECR: Spring Boot backend image repository
- S3: uploaded file object bucket
- IAM: application S3 access policy
- Security Groups: ALB, application, Aurora, Redis 접근 경계
- ECS: cluster, Fargate service, task definition, task role, ALB
- GitHub Actions: OIDC provider, deploy role, ECR/ECS 배포 권한
- Aurora PostgreSQL: database subnet group, encrypted Serverless v2 cluster
- ElastiCache Redis: database subnet group, encrypted replication group

## 예정 구성

- Route53 hosted zone record
- ACM certificate
- CloudWatch log group, metric alarm
- Lambda 후처리 실행 역할과 S3 event notification

## 개발 환경 실행

예시 변수 파일을 복사한 뒤 bucket 이름처럼 전역 유일성이 필요한 값을 수정한다.

```bash
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
```

원격 state를 사용할 때는 `backend.tf.example`을 `backend.tf`로 복사하고 state bucket과 lock table 이름을 실제 값으로 바꾼다.

## 변수 체계

- `aws_region`: 배포 리전. 기본값은 `ap-northeast-2`
- `project_name`: 리소스 이름 prefix
- `environment`: 환경명. 현재는 `dev`
- `vpc_cidr`: VPC 전체 CIDR
- `public_subnets`: ALB/NAT용 subnet CIDR 목록
- `private_subnets`: ECS 애플리케이션용 subnet CIDR 목록
- `database_subnets`: Aurora/ElastiCache용 subnet CIDR 목록
- `file_bucket_name`: 업로드 파일 저장 S3 bucket 이름
- `force_destroy_buckets`: 개발 bucket 삭제 허용 여부
- `ecs_backend_image_tag`: ECS 서비스가 배포할 backend image tag
- `ecs_container_port`: backend 컨테이너 포트
- `ecs_task_cpu`: Fargate task CPU units
- `ecs_task_memory`: Fargate task memory
- `ecs_desired_count`: ECS service desired task count
- `app_jwt_secret`: AWS 실행 환경의 JWT secret
- `github_repository`: GitHub Actions OIDC assume을 허용할 repository
- `github_branch`: GitHub Actions OIDC assume을 허용할 branch
- `aurora_master_password`: Aurora master password. 실제 값은 `terraform.tfvars` 또는 secret store에서 주입한다.
- `redis_node_type`: ElastiCache Redis node type
