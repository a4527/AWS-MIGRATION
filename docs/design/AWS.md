이 문서는 Terraform으로 구성한 AWS 개발 환경을 자세히 설명한다.

초기 설계는 다른 컨테이너 오케스트레이션 방식을 고려했지만, 현재 기준의 애플리케이션 실행 환경은 ECS Fargate이다. 따라서 이 문서는 ECS, ALB, Aurora, ElastiCache, S3, ECR, IAM, Security Group, VPC를 기준으로 정리한다.

## 핵심 결론

현재 Terraform 구성의 목표는 온프레미스 Docker Compose 환경을 AWS 관리형 서비스 조합으로 옮기는 것이다.

```text
온프레미스
  Nginx
  Spring Boot container
  PostgreSQL
  Redis
  MinIO

AWS
  ALB
  ECS Fargate
  Aurora PostgreSQL
  ElastiCache Redis
  S3
```

Spring Boot 애플리케이션 코드는 최대한 그대로 유지한다. 변경되는 것은 실행 환경과 외부 의존성이다.

- 로컬 파일/MinIO 저장소는 S3로 전환한다.
- PostgreSQL은 Aurora PostgreSQL로 전환한다.
- Redis는 ElastiCache Redis로 전환한다.
- Docker Compose의 애플리케이션 컨테이너는 ECS Fargate task로 전환한다.
- Nginx reverse proxy 역할은 ALB가 담당한다.
- S3 object 생성 후처리는 Lambda와 CloudWatch로 분리한다.
- access key 기반 AWS 접근은 쓰지 않고 ECS task role을 사용한다.

## 전체 요청 흐름

AWS 배포 후 사용자의 API 요청은 다음 순서로 이동한다.

```text
Client
  -> Route53
  -> ALB
  -> ECS target group
  -> ECS Fargate task
  -> Spring Boot backend
  -> Aurora PostgreSQL
  -> ElastiCache Redis
  -> S3
  -> S3 ObjectCreated event
  -> Lambda file processor
  -> S3 object tag / CloudWatch Logs
```

Route53과 ACM HTTPS listener는 조건부로 생성한다. `domain_name`과 `route53_hosted_zone_name`이 비어 있으면 public ALB의 HTTP listener만 사용하고, 두 값이 설정되면 HTTPS listener와 HTTP to HTTPS redirect를 적용한다.

## Terraform 디렉터리 구조

```text
terraform/
├── environments/
│   ├── dev-data/          # 유지 계층: VPC·S3·Aurora
│   │   ├── .terraform.lock.hcl
│   │   ├── backend.tf.example
│   │   ├── main.tf
│   │   ├── outputs.tf
│   │   ├── providers.tf
│   │   ├── terraform.tfvars.example
│   │   └── variables.tf
│   └── dev/               # 실습 계층: NAT·ECS·Redis 등
│       ├── backend.tf.example
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

루트 모듈은 두 개이고 state도 따로 둔다([DEC-008](../DECISIONS.md#결정-목록)). `terraform plan`과 `terraform apply`는 각 디렉터리에서 실행한다.

`terraform/modules/*`는 재사용 가능한 하위 모듈이다. 하위 모듈은 단독으로 실행하는 영역이 아니라, 루트 모듈의 `main.tf`에서 호출될 때 실제 인프라 구성에 포함된다.

## 유지 계층과 실습 계층

실습이 끝날 때마다 비용이 큰 리소스는 지우고, 데이터는 남긴다.

| 계층 | 디렉터리 | 리소스 | 수명 | 쉬는 동안 비용 |
| --- | --- | --- | --- | --- |
| 유지 | `environments/dev-data` | VPC·IGW·subnet·route table, S3 file bucket, Aurora cluster·instance·subnet group·SG, SSM DB 비밀번호, ECR, GitHub Actions OIDC provider·배포 role, CloudTrail trail·로그 bucket·변경 알림 EventBridge 규칙(SEC-03) | 계속 유지. Aurora는 `deletion_protection`으로 실수 삭제를 막는다 | S3·Aurora 스토리지·ECR 이미지(현재 규모 몇 센트). VPC·subnet·IGW·SG·IAM은 무료 |
| 실습 | `environments/dev` | NAT Gateway·EIP·private 기본 경로, ALB·ECS·IAM(ECS role)·Lambda·S3 알림, ElastiCache, Aurora SG의 application 허용 규칙, SSM JWT secret | 실습마다 apply → destroy | 없음 |

- Aurora는 `min_capacity = 0`이라 연결이 없으면 `seconds_until_auto_pause`(기본 300초) 뒤 컴퓨트를 멈춘다. 멈춘 동안은 스토리지만 과금되고, 첫 연결에서 다시 깨어나는 데 약 15초가 걸린다(AWS 설명 기준, 실측 전). ECS가 떠 있는 동안은 연결 풀이 연결을 유지하므로 멈추지 않는다.
- 실습 계층은 유지 계층 리소스를 state가 아니라 이름·태그로 조회한다(`dev/data.tf`). 그래서 IAC-01에서 backend를 S3로 바꿨어도 계층 간 연결은 그대로다. state는 `terraform/bootstrap`이 만든 bucket에 계층별 key로 있고 S3 자체 잠금을 쓴다([DEC-011](../DECISIONS.md#결정-목록)).
- 승인된 미구현: 공유 계정 정리 정책에 대비해 유지 계층의 S3 객체와 DB dump를 개인 AWS 계정으로 교차 계정 복제한다. 실행 중 교차 계정 접근은 없다([DEC-012](../DECISIONS.md#결정-목록), [ADR-002](../adr/002-CROSS-ACCOUNT-BACKUP.md), BACKUP-01). 대신 두 계층의 `project_name`·`environment`·`file_bucket_name`이 같아야 한다.
- 계층에 걸친 연결은 실습 계층이 만들고 지운다: NAT 경로(`aws_route`)는 유지 계층의 private route table에, `5432 from Application SG` 규칙은 유지 계층의 Aurora SG에 붙는다. 실습 계층을 지우면 Aurora로 들어오는 규칙이 없어진다.
- Aurora 비밀번호는 유지 계층이 만들어 SSM SecureString에 두고, 실습 계층은 parameter ARN만 task definition에 넣는다(SEC-01, [DEC-013](../DECISIONS.md#결정-목록)). JWT secret parameter는 실습 계층이 만들고 지운다.
- 순서: 생성은 `dev-data` → `dev`, 삭제는 `dev`만. 유지 계층을 지우려면 `aurora_deletion_protection = false`를 먼저 apply하고, 기본값으로 최종 스냅샷(`<prefix>-aurora-final`)을 남긴다.

## 루트 모듈 역할

`terraform/environments/dev-data/main.tf`는 `vpc`, `s3`, `aurora` 모듈을 호출한다.

`terraform/environments/dev/main.tf`는 실습 계층을 조립한다. 호출하는 모듈은 다음과 같다.

- `ecr`
- `nat`
- `iam`
- `file_processor`
- `security_groups`
- `ecs`
- `github_actions`
- `elasticache`

실습 계층은 유지 계층 리소스를 `data.tf`의 data source로 찾아 모듈 input으로 넘긴다.

```text
data.aws_subnets.public    -> local.public_subnet_ids   -> module.ecs, module.nat
data.aws_subnets.private   -> local.private_subnet_ids  -> module.ecs
data.aws_subnets.database  -> local.database_subnet_ids -> module.elasticache
data.aws_route_table.private -> module.nat (NAT 기본 경로)
data.aws_security_group.aurora -> module.security_groups (5432 허용 규칙)
data.aws_rds_cluster.this  -> module.ecs.database_url, database_username
```

`aws_subnets`는 순서를 보장하지 않으므로 ID를 정렬해 쓴다. NAT는 정렬 후 첫 public subnet에 둔다.

S3 bucket ARN은 IAM 모듈로 전달되고, IAM 모듈이 만든 policy ARN은 ECS task role에 연결된다.

```text
data.aws_s3_bucket.files.arn
  -> module.iam.file_bucket_arn
  -> module.iam.application_files_policy_arn
  -> module.ecs.app_policy_arns
  -> ECS task role policy attachment
```

또한 S3 bucket ID와 ARN은 file processor Lambda 모듈로 전달된다.

```text
data.aws_s3_bucket.files.id
data.aws_s3_bucket.files.arn
  -> module.file_processor
  -> Lambda permission
  -> S3 bucket notification
```

ECR과 GitHub Actions 모듈은 유지 계층에 있다([DEC-015](../DECISIONS.md#결정-목록)). GitHub Actions 모듈은 CI/CD가 ECR에 image를 push하고 ECS service를 업데이트할 수 있는 deploy role을 만든다. ECS cluster·service·role ARN은 실습 계층이 없을 때도 정책을 만들 수 있도록 이름 규칙으로 만든다. 실습 계층은 ECR을 이름으로 조회한다.

```text
module.ecr.repository_arns["backend"]
  -> module.github_actions.ecr_repository_arn

module.ecs.cluster_arn
  -> module.github_actions.ecs_cluster_arn

module.ecs.service_arn
  -> module.github_actions.ecs_service_arn

module.ecs.task_role_arn
module.ecs.execution_role_arn
  -> module.github_actions iam:PassRole 허용 대상
```

## 루트 main.tf 연결 방식

`terraform/environments/dev/main.tf`는 하위 모듈을 직접 구현하는 파일이 아니라, 하위 모듈을 호출하고 서로 필요한 값을 넘겨주는 조립 파일이다.

하위 모듈끼리는 직접 서로를 참조하지 않는다. 예를 들어 ECS 모듈 안에서 bucket 이름을 직접 찾지 않는다. 대신 루트 모듈이 S3 bucket data source 값을 ECS 모듈 input으로 넘긴다.

```text
하위 모듈 A
  -> output
  -> 루트 main.tf
  -> input
  -> 하위 모듈 B
```

따라서 값 전달 흐름은 다음 형태가 된다.

```text
terraform.tfvars
  -> root variable
  -> root main.tf module argument
  -> child module variable
  -> child module resource
```

예를 들어 ECS desired count는 다음 순서로 전달된다.

```text
terraform.tfvars
  ecs_desired_count = 1

terraform/environments/dev/variables.tf
  variable "ecs_desired_count"

terraform/environments/dev/main.tf
  module "ecs" {
    desired_count = var.ecs_desired_count
  }

terraform/modules/ecs/variables.tf
  variable "desired_count"

terraform/modules/ecs/main.tf
  aws_ecs_service.application.desired_count = var.desired_count
```

즉 루트 `main.tf`의 핵심 책임은 다음과 같다.

- 어떤 하위 모듈을 사용할지 결정한다.
- `terraform.tfvars`에서 들어온 값을 하위 모듈 입력값으로 넘긴다.
- 한 하위 모듈의 output을 다른 하위 모듈의 input으로 연결한다.
- Route53/ACM처럼 특정 환경에만 필요한 루트 리소스를 조건부로 만든다.
- 최종적으로 개발 환경 전체 리소스 그래프를 만든다.

실습 계층(`dev/main.tf`)의 현재 주요 연결은 다음과 같다. 유지 계층(`dev-data/main.tf`)은 `module.vpc -> module.aurora`만 연결한다.

```text
data.aws_vpc / data.aws_subnets / data.aws_route_table (유지 계층)
  -> module.nat
  -> module.security_groups
  -> module.ecs
  -> module.elasticache

data.aws_security_group.aurora (유지 계층)
  -> module.security_groups 5432 허용 규칙

module.ecr
  -> module.ecs container_image
  -> module.github_actions deploy policy

data.aws_s3_bucket.files (유지 계층)
  -> module.iam application_files policy
  -> module.file_processor S3 notification
  -> module.ecs S3_BUCKET environment variable

module.iam
  -> module.ecs task role policy attachment

data.aws_rds_cluster.this (유지 계층)
  -> module.ecs DB_URL environment variable

module.nat
  -> module.ecs (depends_on)

module.elasticache
  -> module.ecs REDIS_HOST environment variable

aws_acm_certificate_validation.application
  -> module.ecs ALB HTTPS listener

module.ecs
  -> aws_route53_record.application
  -> module.github_actions ECS deploy target
```

이 구조에서는 루트 모듈이 환경별 차이를 담당한다. 예를 들어 dev 환경은 `terraform/environments/dev`의 변수와 `main.tf` 조립 방식으로 결정되고, 나중에 prod 환경을 만든다면 별도 루트 모듈에서 같은 하위 모듈을 다른 입력값으로 호출할 수 있다.

## 이름 규칙

루트 모듈은 `locals`에서 공통 이름 prefix를 만든다.

```hcl
locals {
  name_prefix = "${var.project_name}-${var.environment}"
}
```

기본값 기준으로 `project_name = "fileshare"`, `environment = "dev"`이면 prefix는 다음과 같다.

```text
fileshare-dev
```

따라서 주요 리소스 이름은 다음 형태가 된다.

```text
fileshare-dev-vpc
fileshare-dev-alb
fileshare-dev-ecs
fileshare-dev-backend
fileshare-dev-aurora
fileshare-dev-redis
fileshare-dev-application-files
```

## 공통 태그

**모든 리소스에 `Owner` 태그가 있어야 한다.** 이 AWS 계정은 Owner 태그가 없는 리소스를 삭제할 수 있다([DEC-009](../DECISIONS.md#결정-목록)). 그래서 Owner는 두 군데서 붙인다.

- provider `default_tags`: 태그를 지원하는 모든 리소스에 `Project`·`Environment`·`ManagedBy`·`Owner`를 붙인다. 유지 계층은 `Layer = data`도 붙인다.
- 루트 모듈의 `common_tags`: 모듈에 넘기는 태그다. `merge(var.tags, {...})`에서 `Owner = var.owner`를 마지막에 넣는다. 그래서 `tags`에 다른 Owner를 넣어도 덮어써지지 않는다. 리소스 단위 태그는 provider 기본 태그보다 우선하기 때문에 이 순서가 필요하다.

```text
Project     = fileshare
Environment = dev
ManagedBy   = terraform
Owner       = <var.owner>
```

`owner` 변수는 기본값이 없고 빈 값이면 validation이 실패한다. 실제 값은 gitignore된 `terraform.tfvars`에만 둔다.

Terraform 밖에서 생기는 리소스도 Owner를 갖게 했다.

| 리소스 | 방법 |
| --- | --- |
| Fargate task | ECS service의 `propagate_tags = "SERVICE"`, `enable_ecs_managed_tags = true` |
| CD가 등록하는 task definition revision | workflow가 현재 revision의 태그를 `--include TAGS`로 읽어 새 revision에 붙인다. Owner가 없으면 배포를 실패시킨다. deploy role에 등록 시에만 쓸 수 있는 `ecs:TagResource`를 준다 |
| Aurora 자동 백업·최종 스냅샷 | `copy_tags_to_snapshot = true` |

AWS가 태그를 지원하지 않는 부속 설정은 Owner를 붙일 수 없다. route table association, S3 bucket 하위 설정, IAM policy attachment, Lambda permission, S3 notification, `aws_route`, scaling policy, ECR lifecycle policy가 여기에 해당한다. ECR 이미지 자체에도 AWS 태그를 붙일 수 없다. 반면 repository는 태그된다.

## Provider

`terraform/environments/dev/providers.tf`는 AWS provider와 archive provider를 사용한다.

AWS provider는 AWS 리소스 생성을 담당하고, archive provider는 file processor Lambda source를 zip으로 패키징한다.

```hcl
required_providers {
  aws = {
    source  = "hashicorp/aws"
    version = "~> 5.0"
  }
  archive = {
    source  = "hashicorp/archive"
    version = "~> 2.4"
  }
}
```

배포 리전은 `var.aws_region`으로 받는다. 기본값은 `us-east-1`이다(PORT-01, [DEC-006](../DECISIONS.md#결정-목록)).

유지 계층(`dev-data`)은 Aurora 자동 일시정지 설정(`seconds_until_auto_pause`) 때문에 AWS provider `>= 5.80, < 6.0`을 요구하고 archive provider는 쓰지 않는다.

## VPC 설계

VPC는 개발 환경 기준 `10.20.0.0/16` CIDR을 사용한다.

Subnet은 3계층으로 나눈다.

```text
public subnet
  - ALB
  - NAT Gateway

private subnet
  - ECS Fargate task

database subnet
  - Aurora PostgreSQL
  - ElastiCache Redis
```

기본 CIDR은 다음과 같다.

```text
VPC              10.20.0.0/16
public subnet    10.20.0.0/24, 10.20.1.0/24
private subnet   10.20.10.0/24, 10.20.11.0/24
database subnet  10.20.20.0/24, 10.20.21.0/24
```

AZ는 기본적으로 `us-east-1a`, `us-east-1b`를 사용한다.

## Public Subnet

Public subnet에는 인터넷에서 접근 가능한 리소스를 둔다.

현재 public subnet에 배치되는 리소스는 다음과 같다.

- Internet Gateway
- NAT Gateway
- ALB

ALB는 public subnet에 있어야 클라이언트 요청을 받을 수 있다. NAT Gateway도 public subnet에 있어야 private subnet의 outbound 트래픽을 인터넷으로 전달할 수 있다.

## Private Subnet

Private subnet에는 ECS Fargate task를 둔다.

ECS task는 public IP를 갖지 않는다.

```hcl
assign_public_ip = false
```

이 구조에서는 외부 사용자가 ECS task에 직접 접근할 수 없다. 사용자는 반드시 ALB를 통해 들어와야 한다.

ECS task가 ECR에서 이미지를 pull하거나 CloudWatch Logs로 로그를 전송하거나 외부 AWS API를 호출해야 할 때는 NAT Gateway를 통해 outbound 통신한다.

## Database Subnet

Database subnet에는 Aurora PostgreSQL과 ElastiCache Redis를 둔다.

Database subnet에는 인터넷으로 나가는 기본 route를 두지 않는다. DB와 캐시는 public endpoint로 노출되지 않고, security group도 application SG에서 오는 트래픽만 허용한다.

이 설계의 목적은 다음과 같다.

- DB와 Redis를 인터넷에서 직접 접근 불가능하게 만든다.
- 애플리케이션 계층을 통하지 않는 접근을 차단한다.
- 네트워크 계층과 security group 계층에서 이중으로 접근 범위를 줄인다.

## NAT Gateway

NAT Gateway는 실습 계층의 `nat` 모듈이 만든다. 유지 계층의 private route table에는 경로가 없고, 실습 계층이 `0.0.0.0/0 -> NAT` 경로를 추가했다가 destroy 때 지운다. 쉬는 동안 NAT 비용이 나가지 않게 하기 위해서다.

현재 개발 환경은 비용 절감을 위해 NAT Gateway 1개로 시작한다.

운영 환경에서는 AZ 장애 격리를 위해 AZ별 NAT Gateway를 검토해야 한다.

개발 환경 1개 NAT Gateway 구조:

```text
private subnet 1
private subnet 2
  -> route table
  -> NAT Gateway in public subnet 1
  -> Internet Gateway
```

이 방식은 비용은 낮지만 NAT Gateway가 위치한 AZ 또는 NAT Gateway 자체에 장애가 나면 private subnet outbound 통신에 영향이 갈 수 있다.

## Security Group 설계

Security Group은 역할별로 분리한다.

```text
ALB SG
Application SG
Aurora SG
Redis SG
```

### ALB SG

ALB SG는 인터넷에서 들어오는 HTTP/HTTPS 트래픽을 허용한다.

현재 Terraform에서는 80과 443을 열어둔다.

```text
Inbound:
  TCP 80  from 0.0.0.0/0
  TCP 443 from 0.0.0.0/0
```

ECS 모듈은 기본적으로 HTTP listener를 만들고, ACM 인증서 ARN이 전달되면 HTTPS listener도 만든다. HTTPS가 켜진 경우 HTTP listener는 443으로 redirect한다.

### Application SG

Application SG는 ECS task에 붙는다.

허용되는 inbound는 ALB SG에서 오는 `8080` 트래픽이다.

```text
Inbound:
  TCP 8080 from ALB SG
```

이 규칙 때문에 외부 사용자는 ECS task에 직접 접근할 수 없고, ALB를 통과한 요청만 애플리케이션으로 들어갈 수 있다.

Outbound는 현재 전체 허용이다.

```text
Outbound:
  all traffic to 0.0.0.0/0
```

이 outbound는 Aurora, Redis, S3, CloudWatch, ECR 접근에 필요하다. 운영 환경에서 더 엄격히 제한하려면 VPC endpoint와 prefix list 기반 egress 제한을 함께 설계해야 한다.

### Aurora SG

Aurora SG는 유지 계층의 Aurora 모듈이 만들고, 허용 규칙은 실습 계층의 security-groups 모듈이 `aws_vpc_security_group_ingress_rule`로 붙인다. PostgreSQL 포트만 허용한다.

```text
Inbound:
  TCP 5432 from Application SG
```

즉 같은 VPC 내부라도 Application SG를 붙이지 않은 리소스는 Aurora에 접근할 수 없다.

### Redis SG

Redis SG는 Redis 포트만 허용한다.

```text
Inbound:
  TCP 6379 from Application SG
```

ElastiCache Redis는 database subnet에 있고, application runtime에서만 접근한다.

## Stateful SG와 NACL

Security Group은 stateful이다.

예를 들어 ECS task가 Aurora에 `5432`로 연결하면 Aurora의 응답 트래픽은 별도 inbound 규칙 없이 자동 허용된다.

반면 NACL은 stateless이다. 이 프로젝트에서는 별도 제한 NACL을 정의하지 않는다. 나중에 NACL을 제한한다면 요청 포트와 응답 ephemeral port를 양방향으로 열어야 한다.

예를 들어 ECS task가 Aurora PostgreSQL에 접속하면 실제 TCP 흐름은 다음과 같다.

```text
요청:
  ECS task 10.20.10.15:52034
    -> Aurora 10.20.20.30:5432

응답:
  Aurora 10.20.20.30:5432
    -> ECS task 10.20.10.15:52034
```

여기서 `52034`는 ECS task가 DB 연결을 시작할 때 임시로 사용한 source port이다. 이런 임시 포트 범위를 ephemeral port라고 부른다.

Security Group은 이 연결 상태를 기억하므로 Aurora SG에 `5432 from Application SG`만 있어도 응답 트래픽은 자동으로 허용된다.

NACL은 연결 상태를 기억하지 않는다. 그래서 NACL을 엄격하게 제한하면 App subnet과 DB subnet 양쪽에 요청 방향과 응답 방향을 모두 명시해야 한다.

Aurora 기준 NACL 예시는 다음과 같다.

```text
App subnet NACL outbound:
  allow TCP 5432 to DB subnet CIDR

DB subnet NACL inbound:
  allow TCP 5432 from App subnet CIDR

DB subnet NACL outbound:
  allow TCP 1024-65535 to App subnet CIDR

App subnet NACL inbound:
  allow TCP 1024-65535 from DB subnet CIDR
```

Redis도 같은 구조이며 대상 포트만 `6379`로 바뀐다.

```text
App subnet NACL outbound:
  allow TCP 6379 to DB subnet CIDR

DB subnet NACL inbound:
  allow TCP 6379 from App subnet CIDR

DB subnet NACL outbound:
  allow TCP 1024-65535 to App subnet CIDR

App subnet NACL inbound:
  allow TCP 1024-65535 from DB subnet CIDR
```

즉 database subnet에 인터넷 outbound route가 없어도 같은 VPC 내부 통신은 VPC local route로 가능하다. 다만 NACL을 제한한다면 database subnet outbound에도 응답용 ephemeral port 허용 규칙이 필요하다.

## ECR 모듈

ECR은 Spring Boot backend Docker image를 저장한다.

현재 루트 모듈은 `backend` repository를 만든다.

```hcl
repositories = {
  backend = {
    image_tag_mutability = "MUTABLE"
    scan_on_push         = true
    force_delete         = true
  }
}
```

`scan_on_push = true`로 이미지를 push할 때 취약점 스캔을 수행한다.

dev repository는 `force_delete = true`로 설정해 실습 후 `terraform destroy`가 남은 image 때문에 실패하지 않게 한다. 운영 repository에서는 image 보존 요구사항에 따라 이 값을 끄거나 별도 정리 절차를 둔다.

Lifecycle policy는 최근 이미지 20개를 보관하도록 설계되어 있다. 오래된 이미지는 자동 정리 대상이 된다.

## S3 모듈

S3는 업로드 파일 저장소이다.

온프레미스의 MinIO bucket 역할을 AWS에서는 S3 bucket이 대체한다.

S3 모듈은 다음 보안 기본값을 가진다.

- Public access block
- Server-side encryption
- Versioning
- Multipart upload 정리 lifecycle rule

애플리케이션의 `storagePath`는 MinIO와 S3 모두 object key를 저장하는 계약으로 유지한다. 따라서 저장소가 바뀌어도 DB 컬럼 계약은 크게 바뀌지 않는다.

S3 bucket notification은 file processor 모듈에서 구성한다. 현재 기본 prefix는 다음과 같다.

```text
files/
```

애플리케이션이 다른 object key prefix를 쓰게 되면 `file_processor_object_prefix` 값을 함께 변경해야 한다.

## IAM 모듈

IAM 모듈은 애플리케이션이 S3 bucket에 접근하기 위한 policy를 만든다.

권한 범위는 업로드 파일 bucket으로 제한한다.

```text
s3:GetObject
s3:PutObject
s3:DeleteObject
s3:ListBucket
```

Object 접근 권한은 `${bucket_arn}/*`에 적용하고, bucket 목록 조회는 bucket ARN 자체에 적용한다.

이 policy는 직접 사용자에게 붙이지 않는다. ECS 모듈의 application task role에 attach한다.

## File Processor Lambda 모듈

File processor 모듈은 S3 업로드 후처리를 담당한다.

생성 리소스는 다음과 같다.

- Lambda execution role
- Lambda IAM policy
- Lambda function
- S3 bucket notification
- Lambda invoke permission for S3
- CloudWatch log group
- Lambda error alarm
- Lambda duration alarm

Lambda execution role에는 두 종류의 IAM policy document가 사용된다.

```text
data.aws_iam_policy_document.lambda_assume_role
data.aws_iam_policy_document.this
```

`aws_iam_policy_document`는 그 자체로 AWS IAM policy 리소스를 생성하지 않는다. Terraform 안에서 IAM policy JSON을 조립하는 data source이며, 이 JSON을 어디에 연결하느냐에 따라 trust policy 또는 permission policy로 쓰인다.

`lambda_assume_role`은 trust policy이다. 이 policy는 "누가 이 role을 assume할 수 있는가"를 정의하며, Lambda service principal인 `lambda.amazonaws.com`에 `sts:AssumeRole`을 허용한다. Trust policy는 role의 필수 속성이므로 별도 attach 리소스를 만들지 않고 `aws_iam_role.this.assume_role_policy`에 직접 들어간다.

```hcl
resource "aws_iam_role" "this" {
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}
```

반면 `data.aws_iam_policy_document.this`는 permission policy이다. 이 policy는 role을 획득한 Lambda가 실제로 수행할 수 있는 작업을 정의한다. 현재 범위는 S3 object 조회 및 tagging, 제한된 prefix의 bucket list, CloudWatch Logs 기록이다. Permission policy는 `aws_iam_policy` 리소스로 만든 뒤 `aws_iam_role_policy_attachment`로 Lambda execution role에 attach한다.

```hcl
resource "aws_iam_policy" "this" {
  policy = data.aws_iam_policy_document.this.json
}

resource "aws_iam_role_policy_attachment" "this" {
  role       = aws_iam_role.this.name
  policy_arn = aws_iam_policy.this.arn
}
```

정리하면 trust policy는 role을 사용할 주체를 정하고 role 생성 시 직접 포함되며, permission policy는 role을 사용한 뒤 허용되는 AWS API 권한을 정하고 별도 policy attachment로 연결된다.

Lambda source는 다음 파일이다.

```text
terraform/functions/file-processor/handler.py
```

Terraform은 `archive` provider로 이 파일을 zip으로 패키징한다.

```text
terraform/environments/dev/.terraform/<name_prefix>-file-processor.zip
```

이 build 산출물은 Terraform 실행 중 생성되는 로컬 패키지이며 Git 추적 대상이 아니다.

Lambda는 `s3:ObjectCreated:*` 이벤트를 받아 object metadata를 조회한 뒤 object tag를 기록한다.

기본 tag는 다음과 같다.

```text
scan-status
processing-status
metadata-size
metadata-content-type
processor
```

현재 구현은 확장자 기반의 기본 가드이다. 기본 차단 확장자는 다음과 같다.

```text
.exe
.bat
.cmd
.sh
```

차단 확장자는 `file_processor_blocked_extensions` 변수로 조정한다.

중요한 제한 사항은 다음과 같다.

- 실제 백신 엔진을 포함하지 않는다.
- DB의 `files.status`, `files.scan_status`를 직접 변경하지 않는다.
- 후속 운영 단계에서 ClamAV layer, 검사 전용 컨테이너, 또는 DB 상태 동기화 경로를 추가해야 한다.

이 제한은 최초 구성 범위를 S3 이벤트, 서버리스 권한, CloudWatch 관측성 연결까지로 둔 결과다. DB 상태 동기화는 FILE-03에서 다룬다.

## GitHub Actions 모듈

GitHub Actions 모듈은 애플리케이션 배포 파이프라인이 AWS에 접근할 수 있는 IAM 구성을 만든다.

생성 리소스는 다음과 같다.

- GitHub Actions OIDC provider
- GitHub Actions deploy role
- ECR/ECS 배포용 IAM policy
- deploy role policy attachment

배포 파이프라인은 장기 access key를 저장하지 않는다. 대신 GitHub Actions가 OIDC token을 발급받고, AWS IAM role을 assume해서 임시 자격 증명을 얻는다.

```text
GitHub Actions
  -> OIDC token
  -> AWS IAM OIDC provider
  -> fileshare-dev-github-actions-deploy role
  -> temporary AWS credentials
  -> ECR push
  -> ECS service update
```

Trust policy는 특정 GitHub repository와 branch로 제한한다.

```text
repo:<owner>/<repo>:ref:refs/heads/main
```

이 값은 `github_repository`, `github_branch` 변수로 제어한다.

Deploy role 권한은 애플리케이션 배포에 필요한 범위로 제한한다.

```text
ECR:
  ecr:GetAuthorizationToken
  ecr:BatchCheckLayerAvailability
  ecr:InitiateLayerUpload
  ecr:UploadLayerPart
  ecr:CompleteLayerUpload
  ecr:PutImage
  ecr:DescribeImages
  ecr:DescribeRepositories

ECS:
  ecs:DescribeClusters
  ecs:DescribeServices
  ecs:DescribeTaskDefinition
  ecs:RegisterTaskDefinition
  ecs:UpdateService

IAM:
  iam:PassRole
```

`iam:PassRole`은 ECS task definition을 새 revision으로 등록할 때 필요하다. GitHub Actions가 임의의 role을 넘기지 못하도록 application task role과 execution role만 허용한다.

Terraform apply 후에는 다음 output 값을 GitHub Actions secret으로 등록한다.

```bash
terraform output github_actions_deploy_role_arn
```

GitHub repository secret 이름은 다음과 같다.

```text
AWS_DEPLOY_ROLE_ARN
```

## ECS 모듈 개요

ECS 모듈은 Spring Boot backend를 Fargate로 실행하기 위한 핵심 모듈이다.

생성 리소스는 다음과 같다.

- ECS cluster
- ECS task execution role
- ECS application task role
- S3 접근 policy attachment
- CloudWatch log group
- ALB
- ALB target group
- ALB HTTP listener
- ECS task definition
- ECS service
- Service Auto Scaling target
- CPU/memory target tracking policy
- ECS CPU/memory alarm
- ALB target 5xx alarm

## ECS Cluster

ECS cluster 이름은 다음 형식이다.

```text
<name_prefix>-ecs
```

예:

```text
fileshare-dev-ecs
```

Cluster setting에서는 Container Insights를 켠다.

```hcl
setting {
  name  = "containerInsights"
  value = "enabled"
}
```

Container Insights는 ECS task와 service의 CPU, memory, network 사용량 관찰에 도움이 된다. 비용이 추가될 수 있으므로 운영에서는 필요한 지표와 로그 보존 기간을 함께 검토해야 한다.

## ECS Task Execution Role

Execution role은 애플리케이션 코드가 쓰는 role이 아니다.

이 role은 ECS agent가 task 실행을 준비할 때 사용한다.

주요 책임은 다음과 같다.

- ECR image pull
- CloudWatch Logs stream 생성
- CloudWatch Logs에 container log 전송

Terraform은 AWS managed policy를 attach한다.

```text
arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

## ECS Application Task Role

Task role은 애플리케이션 컨테이너 내부 코드가 사용하는 AWS 권한이다.

Spring Boot의 `S3FileStorage`가 AWS SDK를 통해 S3에 접근하면, access key가 아니라 이 task role의 임시 자격 증명을 사용한다.

흐름은 다음과 같다.

```text
Spring Boot container
  -> AWS SDK default credential chain
  -> ECS task metadata endpoint
  -> task role temporary credentials
  -> S3 API
```

이 구조의 장점은 다음과 같다.

- access key를 컨테이너 환경변수로 넣지 않아도 된다.
- key rotation을 직접 운영하지 않아도 된다.
- 권한 범위를 task 단위로 제한할 수 있다.
- S3 접근 권한을 Terraform IAM policy로 추적할 수 있다.

## CloudWatch Logs

ECS 모듈은 애플리케이션 로그 그룹을 만든다.

```text
/ecs/<name_prefix>-backend
```

예:

```text
/ecs/fileshare-dev-backend
```

로그 보존 기간은 기본 14일이다.

```hcl
retention_in_days = var.log_retention_in_days
```

개발 환경에서는 비용과 디버깅 편의성의 균형을 위해 짧은 보존 기간을 둔다. 운영 환경에서는 감사와 장애 분석 요구사항에 맞춰 늘릴 수 있다.

로그 그룹은 실습 계층에 있어 `dev`를 destroy하면 보존 기간과 관계없이 함께 지워진다. 필요한 근거는 destroy 전에 작업일지로 옮긴다. 지표(CloudWatch metrics)는 리소스를 지워도 최대 15개월 남는다. 로그 형식·요청 ID·연결 구조는 [로그·지표·알람 연결](DEPLOYMENT.md#411-2-로그지표알람-연결과-확인-위치)을 따른다.

## ALB

ALB는 public subnet에 생성된다.

```hcl
internal           = false
load_balancer_type = "application"
```

ALB는 `alb_security_group_id`를 사용한다. 이 SG는 인터넷에서 80/443을 허용한다.

현재 ALB 이름은 다음 형식이다.

```text
<name_prefix>-alb
```

예:

```text
fileshare-dev-alb
```

## Target Group

Target group은 ECS Fargate task를 대상으로 한다.

중요한 설정은 `target_type = "ip"`이다.

Fargate task는 EC2 instance에 직접 붙는 방식이 아니라 awsvpc 네트워크 모드에서 자체 ENI와 private IP를 가진다. 그래서 ALB target group은 instance target이 아니라 ip target이어야 한다.

```hcl
target_type = "ip"
```

Target group port는 컨테이너 포트와 동일하게 `8080`이다.

Health check path는 다음과 같다.

```text
/api/health
```

Spring Boot 애플리케이션의 `HealthController`가 이 경로를 제공한다. 이 endpoint가 200을 반환해야 ALB가 task를 healthy로 판단한다.

Health check 설정과 의미:

| 설정 | 값 | 의미 |
| --- | --- | --- |
| 간격 / 시간 제한 | 30초 / 5초 | |
| healthy 판정 | 2회 연속 성공 | 새 task는 등록 후 최소 약 1분 뒤 healthy |
| unhealthy 판정 | 3회 연속 실패 | 죽은 task 감지에 최대 약 1분 30초 |
| deregistration delay | 300초(기본값, 미설정) | 빠지는 task를 5분 동안 `draining`으로 둔다 |

- 근거 수준: Terraform 초기값이다. 실측 근거로 정한 값이 아니다.
- 2026-11-20 V-AWS-04 실측: task 중지 시 ALB 502/503이 약 87초 이어졌다. 새 task가 정식으로 `healthy`가 되기까지는 그보다 약 1분 더 걸렸다. 그 사이의 200은 ALB fail-open(모든 target이 unhealthy이면 그래도 전달) 덕분이다([작업일지](../worklogs/2026-11-20.md#verify-02-v-aws-04-v-perf)).
- 판단:
  - 간격 10초·healthy 2회·deregistration 30초로 줄이면 복구가 빨라진다.
  - 너무 민감하면 잠깐 느린 task도 빠지고(GC·DB 재개 중), 배포 중 연결이 끊길 수 있다.
  - ECS service의 health check grace period로 기동 시간(실측 약 1분 30초)을 보호하는 방법도 있다.
  - 조정과 재측정은 OPS-01에서 한다.

## Listener

현재 ECS 모듈은 기본 HTTP listener를 만든다.

```text
ALB :80
  -> target group
  -> ECS task :8080
```

도메인 설정이 있는 경우에는 Route53 hosted zone에서 ACM DNS validation을 수행하고 다음 구조로 바뀐다.

```text
ALB :80
  -> 301 redirect to HTTPS

ALB :443
  -> target group
  -> ECS task :8080
```

## Task Definition

Task definition은 ECS가 어떤 컨테이너를 어떤 리소스와 환경변수로 실행할지 정의한다.

현재 주요 설정은 다음과 같다.

```hcl
requires_compatibilities = ["FARGATE"]
network_mode             = "awsvpc"
cpu                      = var.cpu
memory                   = var.memory
```

기본값은 다음과 같다.

```text
ecs_task_cpu    = 512
ecs_task_memory = 1024
```

즉 개발 환경 기준으로 0.5 vCPU, 1GB memory task를 사용한다.

## Container Image

컨테이너 이미지는 ECR repository URL과 image tag를 조합한다.

```hcl
container_image = "${module.ecr.repository_urls["backend"]}:${var.ecs_backend_image_tag}"
```

기본 tag는 `latest`이다.

```text
ecs_backend_image_tag = "latest"
```

실제 운영에서는 `latest`보다 Git SHA, release version, build number 같은 불변 tag를 쓰는 편이 좋다. 그래야 어떤 task definition revision이 어떤 코드를 실행하는지 추적하기 쉽다.

## Container Port

Spring Boot는 기본적으로 `8080`으로 실행한다.

```text
SERVER_PORT=8080
```

Task definition의 `portMappings`도 `8080`을 사용한다.

```text
containerPort = 8080
hostPort      = 8080
protocol      = tcp
```

Fargate awsvpc 모드에서는 task가 자체 network interface를 가지므로 host port 충돌 문제를 EC2 bridge 모드처럼 신경 쓰지 않아도 된다.

## ECS Service

ECS service는 task definition을 기준으로 원하는 task 개수를 유지한다.

```hcl
desired_count = var.desired_count
launch_type   = "FARGATE"
```

기본 desired count는 2이다.

```text
ecs_desired_count = 2
```

2개 task를 두면 하나의 task가 교체되거나 장애가 나도 다른 task가 요청을 받을 수 있다. 다만 개발 환경 비용을 줄이려면 `ecs_desired_count = 1`로 낮출 수 있다.

## ECS Service Auto Scaling

ECS service는 Application Auto Scaling target을 가진다.

기본값은 다음과 같다.

```text
ecs_min_capacity = 2
ecs_max_capacity = 4
```

Target tracking policy는 CPU와 memory를 각각 본다.

```text
ecs_cpu_target_value    = 60
ecs_memory_target_value = 70
```

CPU 또는 memory 평균 사용률이 target을 넘으면 scale out이 발생하고, 낮게 유지되면 cooldown 이후 scale in된다.

현재 실습 계층 tfvars는 `ecs_min_capacity = 1`, `ecs_max_capacity = 4`다.

| 설정 | 값 | 이렇게 둔 이유 | 근거 수준 |
| --- | --- | --- | --- |
| task 범위 | 1~4 | 실습 비용 최소(1), 부하 실험에서 확장을 볼 수 있는 최소 여유(4) | 비용 기준 |
| CPU 목표 | 60% | 새 task 기동(실측 약 1분 30초) 동안 기존 task가 버틸 여유 40% | 출발값, PERF-01 검증 |
| 메모리 목표 | 70% | JVM이 힙을 미리 잡아 두는 특성 때문에 CPU보다 높게 | 출발값, JVM에 부적합할 수 있음 |
| scale out 대기 | 60초 | 부하에는 빨리 대응 | 일반값 |
| scale in 대기 | 300초 | 늘었다 줄었다를 반복(flapping)하지 않도록 천천히 | 일반값 |

동작 규칙(Application Auto Scaling target tracking):

- 정책마다 CloudWatch 알람 2개(`TargetTracking-...-AlarmHigh`/`AlarmLow`)를 AWS가 자동으로 만든다. 이 알람은 Auto Scaling이 관리하므로 직접 고치거나 지우지 않는다.
- scale out: 어느 정책이든 목표를 3분 연속 넘으면 task를 더한다.
- scale in: **모든 정책이** 목표의 90%(CPU 54%, 메모리 63%) 아래로 15분 연속 머물러야 task를 줄인다.
- `AlarmLow`가 `ALARM`인 것은 "줄일 수 있음" 상태라 정상이다. 최소 1개에서는 아무 일도 일어나지 않는다.

판단 기준:

- 정상: 평소 사용률이 목표보다 충분히 낮고, 부하 때 늘었다가 끝나면 줄어든다.
- 늘었다 줄었다를 반복하면 목표가 평소 사용량에 너무 가깝다. 목표를 올리거나 scale in 대기를 늘린다.
- 늘어난 뒤 줄지 않으면 메모리 정책일 가능성이 크다. JVM은 메모리를 잘 돌려주지 않는다. CPU 단독 정책을 검토한다.
- 최대(4)에 붙어 있으면 용량 부족이나 병목이다. 최대를 올리기 전에 원인부터 찾는다.
- 2026-11-20 기준 한 번도 확장되지 않았다(평소 CPU 1~2%, 메모리 27%). 실제 확장·축소는 PERF-01에서 확인한다.

개발 비용을 줄이려면 `ecs_desired_count`, `ecs_min_capacity`, `ecs_max_capacity`를 함께 낮춰야 한다.

```hcl
ecs_desired_count = 1
ecs_min_capacity  = 1
ecs_max_capacity  = 2
```

## CloudWatch Alarms

CloudWatch alarm은 다음 항목을 만든다.

```text
<name_prefix>-file-processor-errors
<name_prefix>-file-processor-duration
<name_prefix>-backend-cpu-high
<name_prefix>-backend-memory-high
<name_prefix>-alb-target-5xx
<name_prefix>-alb-elb-5xx
<name_prefix>-alb-no-healthy-target
```

모든 알람은 유지 계층 SNS topic `<name_prefix>-alarms`로 ALARM과 OK를 이메일로 보낸다([DEC-016](../DECISIONS.md#결정-목록)). 알람별 기준, 이유, 판단, 대응은 [알람 대응](DEPLOYMENT.md#411-1-알람-대응-obs-02)에 있다. 위 `TargetTracking-...` 알람은 Auto Scaling이 만든 것으로 이 목록과 별개다.

## ECS Service Network

ECS service는 private subnet에서 실행된다.

```hcl
network_configuration {
  assign_public_ip = false
  security_groups  = [var.application_security_group_id]
  subnets          = var.private_subnet_ids
}
```

이 설정 때문에 task는 public IP 없이 private IP만 가진다. ALB가 target group을 통해 task private IP로 트래픽을 전달한다.

## ECS Service와 ALB 연결

ECS service는 target group에 연결된다.

```hcl
load_balancer {
  target_group_arn = aws_lb_target_group.application.arn
  container_name   = "backend"
  container_port   = var.container_port
}
```

배포 흐름은 다음과 같다.

```text
ECS service creates task
  -> task gets private IP
  -> ECS registers private IP to target group
  -> ALB health check runs
  -> healthy target receives traffic
```

## Application Profile

AWS 배포에서는 Spring profile을 다음처럼 켠다.

```text
SPRING_PROFILES_ACTIVE=postgres,redis,s3
```

각 profile의 의미는 다음과 같다.

- `postgres`: JPA/PostgreSQL 저장소 사용
- `redis`: Redis 캐시 사용
- `s3`: S3 object storage 사용

이 조합은 온프레미스의 `postgres,redis,minio` 조합에서 MinIO만 S3로 바꾼 것이다.

## ECS 환경변수

Task definition은 Spring Boot에 다음 환경변수를 주입한다.

```text
SPRING_PROFILES_ACTIVE=postgres,redis,s3
AWS_REGION=<aws_region>
S3_BUCKET=<s3_bucket_name>
S3_OBJECT_PREFIX=<file_object_prefix>
DB_URL=jdbc:postgresql://<aurora-endpoint>:5432/<database>
DB_USERNAME=<aurora_master_username>
REDIS_HOST=<elasticache-primary-endpoint>
REDIS_PORT=6379
REDIS_SSL_ENABLED=true
```

다음 두 값은 `secrets`(SSM parameter ARN)로 주입한다.

```text
DB_PASSWORD     <- /<project>/<environment>/aurora/master-password
APP_JWT_SECRET  <- /<project>/<environment>/app/jwt-secret
```

`REDIS_SSL_ENABLED=true`인 이유는 ElastiCache 모듈에서 transit encryption을 켜기 때문이다.

```hcl
transit_encryption_enabled = true
```

## Secret 처리 기준

SEC-01([DEC-013](../DECISIONS.md#결정-목록))로 다음과 같이 구현했다. 절차는 [비밀값 관리](DEPLOYMENT.md#42-1-비밀값-관리-sec-01)에 있다.

- 저장소: SSM Parameter Store SecureString(Standard tier, AWS 관리 키 `aws/ssm`). Secrets Manager(비밀당 월 $0.40)와 RDS 관리 비밀번호는 쓰지 않는다.
- 생성: Terraform ephemeral `random_password`. Aurora `master_password_wo`, SSM `value_wo` 같은 write-only 인자로만 전달해 tfvars·state·plan에 값이 없다. `sensitive = true`는 화면 표시만 가리고 state에는 값을 남기므로 쓰지 않았다.
- 주입: task definition `secrets`. task definition·CD workflow가 복제하는 JSON에는 ARN만 있다.
- 권한: ECS execution role만 두 parameter에 `ssm:GetParameters`. task role·Lambda·GitHub Actions role은 SSM을 읽지 못한다.
- 한계: 자동 교체(rotation)는 없다. 버전 변수를 올려 수동으로 교체하고 task를 새로 배포한다. SEC-01 이전 state 버전에는 예전 비밀번호가 평문으로 남아 있지만 적용 때 교체해서 쓸 수 없다.

## Aurora 모듈

Aurora 모듈은 PostgreSQL 호환 DB를 만든다.

주요 리소스는 다음과 같다.

- DB subnet group
- Aurora PostgreSQL cluster
- Aurora cluster instance

Aurora 모듈은 유지 계층에서 호출한다. Aurora는 database subnet에 배치된다.

```text
database_subnet_ids = module.vpc.database_subnet_ids
```

Security group은 모듈이 만든 Aurora SG만 붙는다. 이 SG에는 모듈 안에서 규칙을 두지 않는다. 실습 계층이 application SG에서 오는 5432 허용 규칙을 붙인다.

삭제 보호는 `deletion_protection`(기본 true)과 최종 스냅샷(`skip_final_snapshot` 기본 false, 식별자 `<prefix>-aurora-final`)으로 한다.

## Aurora Endpoint

Spring Boot의 DB URL은 Aurora writer endpoint를 사용한다.

```hcl
database_url = "jdbc:postgresql://${data.aws_rds_cluster.this.endpoint}:5432/${data.aws_rds_cluster.this.database_name}"
```

결과 형태는 다음과 같다.

```text
jdbc:postgresql://<aurora-writer-endpoint>:5432/fileshare
```

읽기 부하 분리가 필요해지면 reader endpoint를 별도로 사용하도록 애플리케이션 또는 datasource 구성을 확장할 수 있다.

## Aurora Serverless v2

Aurora 모듈은 Serverless v2 scaling 설정을 받는다.

```text
aurora_min_capacity             = 0
aurora_max_capacity             = 2
aurora_seconds_until_auto_pause = 300
```

개발 환경은 최소 용량 0으로 자동 일시정지를 쓴다([DEC-008](../DECISIONS.md#결정-목록)). 멈춘 동안은 컴퓨트 비용이 없고, 0.5 ACU로 계속 두면 us-east-1 기준 월 약 $44가 나간다(2026-11-18 서드파티 가격 자료 기준, 공식 가격 미확인). 일시정지에는 엔진 버전 조건(16.3 이상)이 있다. 엔진 버전은 16.15다. 2026-11-18 us-east-1에서 16.6은 limitless 변형만 남아 있어 온프레미스와 같은 메이저 16의 최신 마이너로 바꿨다. 운영 환경에서는 재개 지연을 피하기 위해 최소 ACU를 0보다 크게 둔다.

## ElastiCache 모듈

ElastiCache 모듈은 Redis replication group을 만든다.

주요 설정은 다음과 같다.

```hcl
engine                     = "redis"
node_type                  = var.node_type
num_cache_clusters         = var.num_cache_clusters
at_rest_encryption_enabled = true
transit_encryption_enabled = true
```

Redis는 database subnet에 배치된다. 실습 계층에 있으므로 subnet은 data source로 받는다.

```text
database_subnet_ids = local.database_subnet_ids
```

Security group은 Redis SG만 붙는다.

```text
security_group_ids = [module.security_groups.redis_security_group_id]
```

Redis SG는 application SG에서 오는 6379만 허용한다.

## Redis TLS

ElastiCache에서 `transit_encryption_enabled = true`를 켰기 때문에 애플리케이션도 TLS 연결을 사용해야 한다.

ECS task definition은 다음 값을 주입한다.

```text
REDIS_SSL_ENABLED=true
```

Spring Boot의 `application-redis.yml`은 이 값을 읽어 Redis SSL 연결을 활성화한다.

## Module Dependency

Terraform은 명시적인 `depends_on`이 없어도 대부분의 의존성을 참조 관계로 계산한다.

예를 들어 ECS 모듈은 Aurora endpoint를 data source로 받는다. 계층 사이에는 Terraform 의존성이 없으므로 유지 계층을 먼저 apply해야 한다. 없으면 data source 조회가 plan 단계에서 실패한다.

실습 계층의 ECS 모듈은 `depends_on = [module.nat]`로 NAT 경로가 생긴 뒤 만들어지고, destroy 때는 ECS가 먼저 지워진 뒤 NAT가 지워진다. task가 ECR 이미지를 받으려면 NAT 경로가 필요하기 때문이다.

ECS service는 listener와 execution role attachment를 명시적으로 기다린다.

```hcl
depends_on = [
  aws_iam_role_policy_attachment.execution,
  aws_lb_listener.http,
]
```

이 설정은 ECS service가 target group과 listener 준비 전에 먼저 생성되는 문제를 줄인다.

## Terraform 변수

유지 계층 변수(`dev-data/variables.tf`):

```text
aws_region
project_name
environment
vpc_cidr
availability_zones
public_subnets
private_subnets
database_subnets
file_bucket_name
force_destroy_buckets
aurora_database_name
aurora_master_username
aurora_master_password_version
aurora_engine_version
aurora_instance_count
aurora_min_capacity
aurora_max_capacity
aurora_seconds_until_auto_pause
aurora_deletion_protection
aurora_skip_final_snapshot
tags
```

실습 계층 변수(`dev/variables.tf`):

```text
aws_region
project_name
environment
file_bucket_name
ecs_backend_image_tag
ecs_container_port
ecs_task_cpu
ecs_task_memory
ecs_desired_count
ecs_health_check_path
app_jwt_secret_version
github_repository
github_branch
github_oidc_thumbprints
redis_node_type
redis_num_cache_clusters
tags
```

`terraform.tfvars`에는 기본값을 덮어써야 하는 환경별 값을 넣는다.

최소로 바꿔야 하는 값은 다음이다.

```hcl
file_bucket_name = "replace-with-globally-unique-fileshare-dev-bucket"
```

S3 bucket 이름은 전 세계에서 유일해야 한다. `file_bucket_name`은 두 계층에서 같은 값을 쓴다. 비밀값은 tfvars에 넣지 않는다(SEC-01).

## Terraform Output

`outputs.tf`는 배포 후 확인하거나 CI/CD에서 사용할 값을 노출한다. 유지 계층은 VPC·subnet·private route table ID, bucket 이름, Aurora 식별자·endpoint·SG ID를 출력한다.

실습 계층의 주요 output은 다음과 같다.

```text
vpc_id
public_subnet_ids
private_subnet_ids
database_subnet_ids
backend_ecr_repository_url
file_bucket_name
application_files_policy_arn
alb_security_group_id
application_security_group_id
ecs_cluster_name
ecs_service_name
github_actions_deploy_role_arn
application_role_arn
application_alb_dns_name
aurora_cluster_endpoint
redis_primary_endpoint
file_processor_function_name
file_processor_log_group_name
```

예를 들어 `backend_ecr_repository_url`은 Docker image push에 사용한다.

`application_alb_dns_name`은 배포 후 health check를 호출할 때 사용한다.

```bash
curl http://<application_alb_dns_name>/api/health
```

`github_actions_deploy_role_arn`은 GitHub Actions의 `AWS_DEPLOY_ROLE_ARN` secret 값으로 사용한다.

## 실행 순서

개발 환경 Terraform 실행은 다음 순서로 진행한다. 유지 계층은 처음 한 번만 만든다.

```bash
cd terraform/environments/dev-data
cp terraform.tfvars.example terraform.tfvars
terraform init && terraform plan && terraform apply

cd ../dev
cp terraform.tfvars.example terraform.tfvars
terraform init && terraform plan && terraform apply
# 실습 종료
terraform destroy   # dev만 지운다
```

단, ECS service가 정상 기동하려면 ECR에 backend image가 있어야 한다.

실제 순서는 다음이 더 안정적이다.

```text
1. terraform init
2. terraform apply로 ECR 포함 기본 인프라 생성
3. backend Docker image build
4. ECR login
5. Docker image push
6. terraform apply 또는 ECS service force deployment
7. ALB health check 확인
```

## 이미지 Push 흐름

ECR repository URL은 Terraform output에서 확인한다.

```bash
terraform output backend_ecr_repository_url
```

이미지 push 흐름은 일반적으로 다음과 같다.

```bash
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

docker build -t fileshare-backend ./backend
docker tag fileshare-backend:latest <repository-url>:latest
docker push <repository-url>:latest
```

CI/CD에서는 GitHub Actions가 이 과정을 수행하게 만들 수 있다.

## Apply 전 주의사항

`terraform apply` 전 확인해야 할 항목은 다음과 같다.

- `terraform.tfvars`의 S3 bucket 이름이 전역 유일한가
- `terraform.tfvars`에 비밀값이 없는가(SEC-01 이후 비밀값 변수는 없다)
- AWS credentials가 올바른 계정과 리전을 가리키는가
- ECR image tag가 실제 push된 tag와 일치하는가
- 예상 비용을 확인했는가

특히 Aurora, ElastiCache, NAT Gateway, ALB, ECS Fargate는 비용이 발생한다.

## 기존 인프라가 있는 경우

이미 이전 구조로 apply한 상태에서 ECS 구조로 바꾸면 Terraform plan에 삭제와 생성이 함께 나타날 수 있다.

확인해야 할 내용은 다음과 같다.

- 제거되는 리소스가 의도한 리소스인가
- 유지해야 할 데이터가 있는 RDS/Aurora 리소스가 삭제 대상이 아닌가
- S3 bucket 삭제가 필요한 경우 `force_destroy_buckets` 설정이 적절한가
- DB final snapshot 정책이 개발/운영 기준에 맞는가

운영 데이터가 있는 환경에서는 `terraform apply` 전에 plan을 반드시 검토해야 한다.

## 비용 관점

현재 구조에서 비용에 특히 영향을 주는 리소스는 다음이다.

- NAT Gateway
- ALB
- ECS Fargate vCPU/memory
- Aurora Serverless v2 ACU
- ElastiCache node
- CloudWatch Logs
- S3 storage/request

개발 비용을 낮추려면 다음을 검토할 수 있다.

- `ecs_desired_count = 1`
- Aurora 최소 ACU 0(자동 일시정지)
- Redis node type을 작은 값으로 유지
- CloudWatch log retention 짧게 유지
- 사용하지 않을 때 실습 계층(`dev`)만 `terraform destroy`

유지 계층을 남겨 둔 동안의 비용은 S3·Aurora 스토리지와 Aurora 자동 백업(보존 1일)이다. 현재 데이터 규모에서는 월 몇 센트 수준으로 추정하며, 실제 청구액은 PORT-01의 예산 알림과 Cost Explorer로 확인한다.

## 보안 관점

현재 구조의 보안 기준은 다음이다.

- DB와 Redis는 database subnet에 둔다.
- ECS task는 private subnet에 둔다.
- 외부 요청은 ALB를 통해서만 들어온다.
- DB/Redis SG는 application SG만 허용한다.
- S3 접근은 ECS task role만 사용하며 object resource는 업로드 prefix로 제한한다.
- Lambda file processor의 S3 read/tagging 권한도 notification prefix로 제한한다.
- 도메인 설정이 있는 환경에서는 ALB HTTPS listener와 HTTP to HTTPS redirect를 적용한다.
- access key를 애플리케이션에 주입하지 않는다.

후속 보강할 보안 항목은 다음이다.

- VPC endpoint로 S3/ECR/CloudWatch 접근 최적화
- ALB access log 활성화
- WAF 적용 검토
- 추가 IAM condition과 egress 제한 검토

## 운영 관점

관측 구성(대시보드·저장 쿼리·알람 연결·증상별 확인 위치)은 [DEPLOYMENT 4.11-1·4.11-2](DEPLOYMENT.md#411-1-알람-대응-obs-02), SLI는 [VALIDATION 11장](VALIDATION.md#11-관측-지표와-sli)을 따른다. 아래는 요약이다.

운영에서 확인할 주요 지표는 다음이다.

- ALB target healthy count
- ALB 4xx/5xx count
- ECS service desired/running task count
- ECS task CPU/memory usage
- ECS task restart 횟수
- Aurora connection count
- Aurora CPU/ACU 사용량
- Redis CPU/memory/connection
- CloudWatch application error log

장애가 발생했을 때는 다음 순서로 확인한다.

```text
ALB target health
  -> ECS service events
  -> ECS task stopped reason (중지 후 약 1시간만 조회 가능)
  -> CloudWatch container logs
  -> CloudTrail (누가 StopTask·UpdateService를 했는지)
  -> Aurora/Redis security group
  -> application environment variables
```

## 현재 한계

현재 Terraform은 ECS 기반 실행에 필요한 1차 인프라를 구성하지만, 운영 완성형은 아니다.

아직 후속으로 보강해야 할 항목은 다음이다.

- 비밀값 자동 교체(rotation) 없음. SSM parameter 버전을 올려 수동 교체한다
- ALB access log
- WAF
- VPC endpoint
- 실제 바이러스 검사 엔진 연동
- Lambda 후처리 결과와 DB 상태 동기화
- 로컬에서 Aurora에 접근하는 경로가 없다. MIG-01에서 데이터 이전을 하지 않기로 해서([DEC-007](../DECISIONS.md#결정-목록)) 만들지 않았다. DB를 직접 조회해야 하면 ECS task나 별도 경로를 추가로 정한다.
- 계층 간 값 중 bucket 이름은 두 tfvars에 중복해서 둔다. DB 비밀번호 중복은 SEC-01에서 SSM parameter 참조로 없앴다.

## 최종 요약

현재 Terraform 구조는 Docker Compose 기반 온프레미스 애플리케이션을 AWS 관리형 서비스로 옮기는 기본 골격이다.

가장 중요한 설계 포인트는 다음이다.

- 실행 환경은 ECS Fargate이다.
- 외부 진입점은 ALB이다.
- 애플리케이션 task는 private subnet에서 실행된다.
- DB와 Redis는 database subnet에 격리된다.
- S3 접근은 ECS task role로 처리하고 업로드 prefix로 제한한다.
- 도메인 설정 시 Route53, ACM, ALB HTTPS listener가 연결된다.
- Terraform 루트 모듈은 `terraform/environments/dev`이다.
- `terraform.tfvars`는 변수 값만 공급하고, 실제 리소스 구조는 `.tf` 파일과 모듈들이 결정한다.

이 구조를 기준으로 다음 단계에서는 VPC endpoint, ALB access log, WAF, 실제 바이러스 검사와 DB 상태 동기화를 보강하면 된다.
