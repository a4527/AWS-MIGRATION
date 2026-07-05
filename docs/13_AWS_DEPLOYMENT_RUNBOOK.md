# 13. AWS Deployment Runbook

이 문서는 AWS 리소스 생성, GitHub Actions 배포, 기능 테스트, 정리 절차를 순서대로 실행하기 위한 런북이다.

실습 비용이 발생하므로 테스트가 끝나면 `terraform destroy`로 정리한다.

## 0. 사전 준비

로컬에 다음 도구가 필요하다.

```text
aws cli
terraform
docker
git
curl
```

GitHub repository도 먼저 준비되어 있어야 한다.

```text
https://github.com/<owner>/aws-migration-project
```

## 1. AWS 로그인 확인

로컬 터미널에서 AWS에 로그인한다.

Access key 방식:

```bash
aws configure
```

SSO 방식:

```bash
aws configure sso
aws sso login
```

현재 로그인된 AWS 계정을 확인한다.

```bash
aws sts get-caller-identity
```

출력의 `Account` 값이 리소스가 생성될 AWS 계정이다.

## 2. Terraform 변수 파일 작성

Terraform dev 환경으로 이동한다.

```bash
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars`에서 최소 다음 값을 수정한다.

```hcl
file_bucket_name       = "replace-with-globally-unique-bucket-name"
aurora_master_password = "replace-with-strong-development-password"
app_jwt_secret         = "replace-with-strong-development-jwt-secret"

github_repository = "<github-owner>/aws-migration-project"
github_branch     = "main"

ecs_desired_count = 1
```

`file_bucket_name`은 전 세계에서 유일해야 한다.

실습 비용을 줄이려면 `ecs_desired_count = 1`로 둔다.

## 3. Terraform 검증

```bash
terraform init
terraform fmt -recursive ../..
terraform validate
terraform plan
```

`terraform plan`에서 생성될 리소스를 확인한다.

주요 생성 대상:

```text
VPC / subnet / route table
NAT Gateway
ALB / target group
ECR repository
ECS cluster / service / task definition
Aurora PostgreSQL
ElastiCache Redis
S3 bucket
IAM roles / policies
GitHub Actions OIDC provider / deploy role
```

## 4. Terraform apply

리소스를 생성한다.

```bash
terraform apply
```

완료 후 output을 확인한다.

```bash
terraform output
```

특히 다음 값을 확인한다.

```bash
terraform output backend_ecr_repository_url
terraform output ecs_cluster_name
terraform output ecs_service_name
terraform output application_alb_dns_name
terraform output github_actions_deploy_role_arn
```

이 시점에는 인프라는 생성되었지만 ECR image가 없으면 ECS task가 정상 실행되지 않을 수 있다.

## 5. GitHub Actions secret 등록

다음 output 값을 복사한다.

```bash
terraform output -raw github_actions_deploy_role_arn
```

GitHub repository에서 secret을 등록한다.

```text
Repository
-> Settings
-> Secrets and variables
-> Actions
-> New repository secret
```

Secret 이름:

```text
AWS_DEPLOY_ROLE_ARN
```

Secret 값:

```text
terraform output -raw github_actions_deploy_role_arn 결과값
```

## 6. GitHub Actions 배포 실행

GitHub repository에서 workflow를 수동 실행한다.

```text
Repository
-> Actions
-> Backend CI/CD
-> Run workflow
-> Branch: main
-> Run workflow
```

Workflow가 수행하는 작업:

```text
backend test
AWS OIDC role assume
ECR login
Docker image build
ECR push
현재 ECS service task definition 조회
backend container image 교체
새 task definition revision 등록
ECS service rolling deployment
```

배포가 성공하면 ECS service가 새 task를 실행하고 ALB target group health check를 통과한 task로 트래픽을 보낸다.

## 7. AWS 콘솔 확인

AWS 콘솔에서 주요 리소스를 확인한다.

```text
ECR
-> Repositories
-> fileshare-dev-backend
-> Images

ECS
-> Clusters
-> fileshare-dev-ecs
-> Services
-> fileshare-dev-backend
-> Tasks

EC2
-> Target Groups
-> fileshare-dev-backend
-> Targets

EC2
-> Load Balancers
-> fileshare-dev-alb

CloudWatch
-> Log groups
-> /ecs/fileshare-dev-backend
```

정상 기준:

```text
ECR image 존재
ECS service running task 존재
Target group target healthy
CloudWatch log stream 생성
```

## 8. ALB DNS 확인

Terraform output으로 ALB DNS를 확인한다.

```bash
ALB_DNS=$(terraform output -raw application_alb_dns_name)
echo "$ALB_DNS"
```

Health API를 호출한다.

```bash
curl "http://$ALB_DNS/api/health"
```

정상 응답 예:

```json
{
  "success": true,
  "data": {
    "status": "UP"
  }
}
```

## 9. 기능 테스트

테스트 파일을 준비한다.

```bash
printf "hello aws deployment\n" > sample.txt
```

회원가입:

```bash
curl -X POST "http://$ALB_DNS/api/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "aws-user@example.com",
    "password": "password123!",
    "name": "AWS User"
  }'
```

로그인:

```bash
curl -s -X POST "http://$ALB_DNS/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "aws-user@example.com",
    "password": "password123!"
  }'
```

응답에서 access token을 복사해 변수에 넣는다.

```bash
TOKEN="replace-with-access-token"
```

내 정보 조회:

```bash
curl "http://$ALB_DNS/api/users/me" \
  -H "Authorization: Bearer $TOKEN"
```

파일 업로드:

```bash
curl -X POST "http://$ALB_DNS/api/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@sample.txt"
```

파일 목록 조회:

```bash
curl "http://$ALB_DNS/api/files" \
  -H "Authorization: Bearer $TOKEN"
```

응답에서 파일 ID를 확인한 뒤 변수에 넣는다.

```bash
FILE_ID="replace-with-file-id"
```

파일 다운로드:

```bash
curl -L "http://$ALB_DNS/api/files/$FILE_ID/download" \
  -H "Authorization: Bearer $TOKEN" \
  -o downloaded-sample.txt
```

파일 삭제:

```bash
curl -X DELETE "http://$ALB_DNS/api/files/$FILE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

## 10. 데이터 확인

AWS 콘솔에서 다음을 확인한다.

```text
S3
-> 업로드 bucket
-> object 생성 여부

RDS
-> Aurora cluster
-> connection metric

ElastiCache
-> Redis metric

CloudWatch Logs
-> backend application logs
```

DB에 직접 접속하려면 private subnet 접근 경로가 필요하다. 현재 database subnet은 public 접근을 허용하지 않으므로, 로컬에서 바로 접속하는 구조가 아니다.

## 11. 문제 발생 시 확인 순서

ALB health check 실패:

```text
EC2 Target Groups
-> target health reason 확인
```

ECS task 실패:

```text
ECS
-> Service events
-> stopped task reason
```

이미지 pull 실패:

```text
ECR image tag 존재 여부
ECS task execution role 권한
Private subnet NAT Gateway route
```

S3 AccessDenied:

```text
ECS task role
application_files_policy
S3 bucket name 환경변수
```

DB 연결 실패:

```text
Aurora endpoint
Application SG -> Aurora SG 5432
DB_URL 환경변수
Aurora master password
```

Redis 연결 실패:

```text
Redis endpoint
Application SG -> Redis SG 6379
REDIS_SSL_ENABLED=true
```

## 12. 정리

실습이 끝나면 비용 방지를 위해 리소스를 삭제한다.

```bash
terraform destroy
```

삭제가 실패할 수 있는 대표 원인:

```text
S3 bucket에 object가 남아 있음
ECR repository에 image가 남아 있음
Aurora final snapshot 설정
```

개발 실습에서 한 번에 삭제하려면 Terraform 변수에서 다음 설정을 검토한다.

```hcl
force_destroy_buckets = true
```

ECR image가 남아 repository 삭제가 막히면 콘솔 또는 CLI로 image를 먼저 삭제한다.

## 13. 비용 주의

1시간 실습도 비용이 발생한다.

비용 발생 리소스:

```text
NAT Gateway
ALB
ECS Fargate
Aurora Serverless v2
ElastiCache Redis
CloudWatch Logs
S3
ECR
```

실습 권장 방식:

```text
terraform apply
GitHub Actions 배포
기능 테스트
terraform destroy
```
