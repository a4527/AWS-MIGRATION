AWS 인프라를 코드로 정의하는 영역입니다.

## 구조

```text
terraform/
├── bootstrap/      # 원격 state용 S3 bucket (로컬 state, 처음 한 번)
├── environments/
│   ├── dev-data/   # 유지 계층: VPC, S3, Aurora (계속 유지)
│   └── dev/        # 실습 계층: NAT, ALB/ECS, ECR, Lambda, Redis (실습마다 apply/destroy)
│       ├── backend.tf            # S3 backend (bucket은 backend.hcl)
│       ├── backend.hcl.example
│       ├── data.tf
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
    ├── file-processor/
    ├── github-actions/
    ├── iam/
    ├── nat/
    ├── s3/
    ├── security-groups/
    └── vpc/
```

## 구성

- VPC
- Public subnet: ALB, NAT Gateway(실습 계층)
- Private subnet: ECS Fargate task
- Database subnet: Aurora PostgreSQL, ElastiCache
- ECR: Spring Boot backend image repository
- S3: uploaded file object bucket
- IAM: application S3 access policy
- Security Groups: ALB, application, Redis(실습 계층), Aurora(유지 계층 SG + 실습 계층 허용 규칙)
- ECS: cluster, Fargate service, task definition, task role, ALB
- GitHub Actions: OIDC provider, deploy role, ECR/ECS 배포 권한
- Aurora PostgreSQL: database subnet group, encrypted Serverless v2 cluster. 최소 0 ACU 자동 일시정지, 삭제 보호
- ElastiCache Redis: database subnet group, encrypted replication group

<a id="예정-구성"></a>

## 추가 구현 구성

아래 항목은 Terraform 코드에 정의되어 있다. 실제 리소스 생성·동작 검증 상태는 [진행 요약](../docs/PROGRESS.md)을 따른다.

- Route53 record·ACM certificate: 도메인 변수 설정 시 활성화되는 선택형 HTTPS
- CloudWatch log group, metric alarm, ECS Service Auto Scaling
- Lambda 후처리 실행 역할과 S3 event notification (`modules/file-processor`)

상세 구조·운영 한계는 [인프라 해설](../docs/design/AWS.md), 실행 순서는 [AWS 배포 런북](../docs/design/DEPLOYMENT.md)을 참조한다.

## 개발 환경 실행

모든 리소스에 `Owner` 태그가 필요하다. 두 계층의 `owner` 변수(필수)가 provider 기본 태그와 `common_tags`에 들어간다([태그 규칙](../docs/design/AWS.md#공통-태그)). 월 예산(`budget_*`)은 유지 계층이 만든다.

두 계층은 state가 따로다([DEC-008](../docs/DECISIONS.md#결정-목록), [계층 설명](../docs/design/AWS.md#유지-계층과-실습-계층)). 유지 계층을 먼저 만들고, 실습 계층은 실습할 때마다 만들고 지운다. 두 계층의 `project_name`·`environment`·`file_bucket_name`·`aurora_master_password`는 같아야 한다.

```bash
cd terraform/bootstrap                  # 처음 한 번: state bucket
cp terraform.tfvars.example terraform.tfvars
terraform init && terraform apply

cd ../environments/dev-data             # 처음 한 번
cp terraform.tfvars.example terraform.tfvars
cp backend.hcl.example backend.hcl      # bucket = bootstrap output
terraform init -backend-config=backend.hcl && terraform plan

cd ../dev                               # 실습마다
cp terraform.tfvars.example terraform.tfvars
cp backend.hcl.example backend.hcl
terraform init -backend-config=backend.hcl && terraform plan
terraform destroy                       # 실습 종료 시 dev만
```

유지 계층의 Aurora는 `deletion_protection = true`라 `terraform destroy`가 실패한다. 정말 지울 때만 `aurora_deletion_protection = false`로 먼저 apply한다.

두 계층의 state는 bootstrap이 만든 S3 bucket에 계층별 key로 저장되고, 잠금은 S3 자체 잠금(`use_lockfile`)이다([DEC-011](../docs/DECISIONS.md#결정-목록)). bucket 이름만 gitignore된 `backend.hcl`에 둔다. 잠금 해제·버전 복구는 [원격 state 준비](../docs/design/DEPLOYMENT.md#41-1-원격-state-준비-iac-01)를 따른다. 오프라인 검증만 할 때는 `terraform init -backend=false`를 쓴다.

## 변수 체계

유지 계층(`dev-data`)만 쓰는 변수: `vpc_cidr`, `availability_zones`, subnet CIDR, `force_destroy_buckets`, `aurora_*`(단, `aurora_master_password`는 두 계층 모두). 실습 계층은 bucket·Aurora를 이름으로 조회한다.

- `aws_region`: 배포 리전. 기본값은 `us-east-1`
- `project_name`: 리소스 이름 prefix
- `environment`: 환경명. 현재는 `dev`
- `vpc_cidr`: VPC 전체 CIDR
- `public_subnets`: ALB/NAT용 subnet CIDR 목록
- `private_subnets`: ECS 애플리케이션용 subnet CIDR 목록
- `database_subnets`: Aurora/ElastiCache용 subnet CIDR 목록
- `file_bucket_name`: 업로드 파일 저장 S3 bucket 이름
- `force_destroy_buckets`: 비어 있지 않은 bucket 삭제 허용 여부(유지 계층, 기본 false)
- `aurora_min_capacity`·`aurora_seconds_until_auto_pause`: 0 ACU 자동 일시정지와 대기 시간(유지 계층, 기본 0·300초)
- `aurora_deletion_protection`·`aurora_skip_final_snapshot`: 유지 계층 삭제 보호(기본 true·false)
- `ecs_backend_image_tag`: 최초 배포·설정 변경용 Terraform task definition 템플릿의 image tag
- `ecs_container_port`: backend 컨테이너 포트
- `ecs_task_cpu`: Fargate task CPU units
- `ecs_task_memory`: Fargate task memory
- `ecs_desired_count`: ECS service 생성 시 task 수. 생성 후에는 Auto Scaling이 관리
- `app_jwt_secret`: AWS 실행 환경의 JWT secret
- `github_repository`: GitHub Actions OIDC assume을 허용할 repository
- `github_branch`: GitHub Actions OIDC assume을 허용할 branch
- `aurora_master_password`: Aurora master password. 두 계층에 같은 값을 둔다. 실제 값은 `terraform.tfvars` 또는 secret store에서 주입한다.
- `redis_node_type`: ElastiCache Redis node type

ECS service의 배포 revision은 CD, 실행 task 수는 Auto Scaling이 관리한다. Terraform의 템플릿 변경은 기존 service에 자동 배포되지 않는다. 최초 배포·설정 변경·재생성 절차와 검증은 [관리 책임과 배포 순서](../docs/design/DEPLOYMENT.md#ecs-관리-책임과-배포-순서-iac-02)를 따른다.
