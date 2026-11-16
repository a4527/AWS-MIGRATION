로컬·온프레미스 실행 설정과 AWS 배포 절차를 관리한다. 배포 후 비교 검증은 [VALIDATION](VALIDATION.md), 실제 수행 범위는 [PROGRESS](../PROGRESS.md)를 따른다.

## 로컬 실행

- Java 17과 Gradle 8.x를 사용한다.
- 백엔드는 `backend/`에서 `./gradlew bootRun`으로 실행한다.
- 테스트는 `backend/`에서 `./gradlew test`로 실행한다.
- Day 3 기준 JWT 설정은 `backend/src/main/resources/application.yml`의 `app.jwt.*` 값을 사용한다.
- Day 4 기준 로컬 파일 저장 경로는 `app.storage.local-root`이며 기본값은 `backend/build/local-storage`이다.
- Day 4 기준 업로드 제한은 `app.file.max-size-bytes`와 `app.file.allowed-extensions`로 조정한다.
- Day 5 기준 외부 저장소 연동은 Spring profile로 분리한다.
- PostgreSQL은 `postgres`, Redis는 `redis`, MinIO는 `minio` 프로필로 활성화한다.
- PostgreSQL 연결값은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 주입한다.
- Redis 연결값은 `REDIS_HOST`, `REDIS_PORT` 환경변수로 주입한다.
- AWS ElastiCache Redis TLS 연결은 `REDIS_SSL_ENABLED=true`로 활성화한다.
- MinIO 연결값은 `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` 환경변수로 주입한다.
- S3 연결값은 `AWS_REGION`, `S3_BUCKET`, `S3_OBJECT_PREFIX` 환경변수로 주입하고 `s3` profile에서 사용한다.
- Day 6 이후 PostgreSQL, Redis, MinIO, Nginx는 Docker Compose로 통합한다.
- Docker Compose 실행 시 외부 API 진입점은 Nginx이며 기본 포트는 `http://localhost:8080`이다.
- MinIO 콘솔은 기본 포트 `http://localhost:9001`로 노출한다.
- `.env.example`을 기준으로 로컬 `.env`를 만들면 포트, 비밀번호, JWT secret, 업로드 제한을 변경할 수 있다.

예시:

```bash
cd backend
SPRING_PROFILES_ACTIVE=postgres,redis,minio ./gradlew bootRun
```

## 온프레미스

- `backend/Dockerfile`로 Spring Boot 애플리케이션 이미지를 빌드한다.
- `docker-compose.yml`로 PostgreSQL, Redis, MinIO, Spring Boot, Nginx를 통합 실행한다.
- Nginx는 `/api/**`와 `/actuator/health` 요청을 Spring Boot 컨테이너로 전달한다.
- 환경변수와 시크릿은 Compose 변수로 분리하고, 기본값은 로컬 개발용으로만 사용한다.
- JWT secret은 로컬 설정 파일에 고정하지 않고 환경변수 또는 compose secret으로 주입한다.

실행:

```bash
docker compose up --build
```

상태 확인:

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

중지:

```bash
docker compose down
```

데이터 볼륨까지 초기화해야 할 때만 다음 명령을 사용한다.

```bash
docker compose down -v
```

## AWS

- Terraform으로 인프라를 생성한다.
- Day 8 기준 Terraform 시작점은 `terraform/environments/dev`이다.
- 개발 환경 VPC는 public, private, database subnet 3계층으로 분리한다.
- ECR로 Spring Boot 이미지를 저장한다.
- S3 bucket은 MinIO를 대체하는 파일 object 저장소로 사용한다.
- IAM policy는 애플리케이션의 S3 접근 권한과 Lambda 실행 권한을 분리한다.
- Day 9 기준 ECS cluster, Fargate service, ALB, application task role을 Terraform으로 생성한다.
- Aurora PostgreSQL과 ElastiCache Redis는 database subnet에 배치하고 application SG에서만 접근하게 한다.
- Spring Boot AWS 실행 profile은 `postgres,redis,s3`를 사용한다.
- ECS service로 애플리케이션을 배포한다.
- ALB로 외부 트래픽을 수신한다.
- ACM으로 HTTPS를 적용한다.
- Route53으로 도메인을 연결한다.
- Lambda 함수 배포 및 S3 이벤트 연결을 구성한다.
- CloudWatch 로그와 알람을 구성한다.
- 바이러스 검사 및 메타데이터 추출 후처리 배포
- 목표는 ECS secrets 참조를 통한 JWT secret 주입이다. 현재 Terraform은 일반 환경변수로 주입하며 Secrets Manager 전환은 SEC-01의 미구현 범위다.

Terraform 실행 예시:

```bash
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
```

AWS 애플리케이션 환경변수 기준:

```bash
SPRING_PROFILES_ACTIVE=postgres,redis,s3
DB_URL=jdbc:postgresql://<aurora-endpoint>:5432/fileshare
DB_USERNAME=fileshare
DB_PASSWORD=<secret>
REDIS_HOST=<elasticache-primary-endpoint>
REDIS_PORT=6379
REDIS_SSL_ENABLED=true
AWS_REGION=ap-northeast-2
S3_BUCKET=<terraform-output-file-bucket-name>
S3_OBJECT_PREFIX=files/
```

## CI/CD

Day 10 기준 CI/CD는 AWS 리소스를 새로 생성하지 않는다. Terraform으로 생성된 ECR repository와 ECS service를 대상으로 backend image를 빌드, 푸시, 배포한다.

GitHub Actions workflow:

```text
.github/workflows/backend-ci-cd.yml
```

CI 흐름:

```text
pull_request(main) / main push / workflow_dispatch
  -> backend test
  -> Docker image build
  -> Trivy image vulnerability scan (HIGH/CRITICAL)
```

배포 흐름:

```text
workflow_dispatch (main)
  -> backend test
  -> Docker image build / Trivy scan
  -> 검증 이미지 artifact 저장 / 배포 job에서 load
  -> AWS OIDC role assume
  -> ECR login
  -> 검증 이미지 tag / ECR push
  -> 현재 ECS service의 task definition 조회
  -> backend container image만 새 image URI로 교체
  -> ECS service rolling deployment
```

필요한 GitHub secret:

```text
AWS_DEPLOY_ROLE_ARN
```

이 값은 Terraform output으로 확인한다.

```bash
cd terraform/environments/dev
terraform output github_actions_deploy_role_arn
```

Terraform은 GitHub Actions가 AWS에 로그인할 수 있도록 다음 리소스도 정의한다.

```text
GitHub Actions OIDC provider
GitHub Actions deploy role
ECR push policy
ECS service deploy policy
iam:PassRole policy for ECS task roles
```

기본 배포 대상:

```text
AWS_REGION=ap-northeast-2
ECR_REPOSITORY=fileshare-dev-backend
ECS_CLUSTER=fileshare-dev-ecs
ECS_SERVICE=fileshare-dev-backend
ECS_CONTAINER_NAME=backend
```

Workflow는 Terraform apply를 실행하지 않는다. 따라서 VPC, ECR, ECS, Aurora, Redis, S3 같은 리소스 생성은 별도 Terraform 단계에서 수행한다. PR과 `main` push는 테스트·이미지 빌드·취약점 검사까지 수행하고, ECR push와 ECS 배포는 `main`의 수동 실행에서만 진행한다. 경로 필터는 `backend/**`와 이 workflow 파일이다.

### PR·이미지 검증과 차단 기준 (CICD-01)

- `test` → `image` → `deploy` 순서다. 테스트·이미지 빌드·검사 실패 시 뒤 job을 실행하지 않는다. 검사 도구나 DB 다운로드 실패도 통과로 처리하지 않는다.
- Trivy는 OS와 애플리케이션 패키지의 HIGH/CRITICAL 취약점을 수정 버전 유무와 관계없이 차단한다. `exit-code: 1`, `ignore-unfixed: false`를 사용하며 자동 예외·실패 무시는 두지 않는다. LOW/MEDIUM은 이 차단 범위 밖이다. 도구 설정은 [Trivy 공식 안내](https://github.com/aquasecurity/trivy-action#usage)를 따른다.
- 예외가 필요하면 해당 취약점의 영향·근거·보완책·담당자·만료일을 PR에 제시하고 명시적 검토를 거친다. 현재 허용한 예외는 없다. 검사 실패를 우회하는 수동 배포 경로도 추가하지 않는다.
- PR은 `pull_request`로 실행한다. 기본 권한은 `contents: read`이며 AWS secret을 참조하지 않는다. `id-token: write`와 AWS role 참조는 배포 job에만 둔다. [GitHub job 권한 기준](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idpermissions)을 따른다.
- `main` 수동 실행은 검사한 Docker 이미지를 1일 보존 artifact로 넘겨 배포 job에서 다시 빌드하지 않는다. 환경 간 digest 승격·승인은 CICD-02에서 검증한다.
- 병합 차단을 보장하려면 저장소 ruleset/branch protection에서 `Test backend`, `Build and scan backend image`를 필수 검사로 등록해야 한다. 경로 필터로 workflow가 생략되는 문서 전용 PR의 처리도 함께 검토한다. 이번 로컬 수정으로 원격 저장소 설정을 바꾸지는 않았다.

실행 확인은 정상 PR과 테스트 실패·이미지 빌드 실패·HIGH/CRITICAL 검출 PR에서 각각 수행한다. 실패 시 `deploy`가 실행되지 않는지, PR job에 OIDC·AWS secret이 전달되지 않는지 확인하고 실제 run 링크를 작업일지에 남긴다. 정적 workflow 검사는 이 실행 증빙을 대신하지 않는다.

ECS rolling deployment는 새 task definition revision을 만들고 service가 새 task를 띄운 뒤, ALB target group health check를 통과한 task로 트래픽을 전환한다.

Day 11 기준 Lambda 리소스와 S3 이벤트 연결은 Terraform에서 생성한다. Lambda 코드 패키징도 Terraform `archive` provider가 수행한다. 별도 Lambda CI/CD workflow는 아직 만들지 않았으며, 운영 전환 시 애플리케이션 배포와 독립적인 서버리스 배포 job으로 분리한다.

Day 12 기준 HTTPS는 선택형으로 구성한다. `terraform.tfvars`에 `domain_name`과 `route53_hosted_zone_name`을 모두 넣으면 Terraform이 ACM 인증서, DNS validation record, ALB HTTPS listener, HTTP to HTTPS redirect, Route53 ALB alias record를 구성한다. 두 값이 비어 있으면 기존 HTTP ALB endpoint를 사용한다.

## 서버리스 배포 기준

- Lambda는 S3 이벤트와 연결되어야 한다.
- Lambda 실행 권한은 최소 권한 원칙으로 분리한다.
- 서버리스 로그는 CloudWatch에서 확인 가능해야 한다.
- 서버리스 배포는 애플리케이션 배포와 독립적으로 롤백 가능해야 한다.
- 현재 file processor Lambda는 object metadata를 읽고 S3 object tag에 후처리 결과를 기록한다.
- 실제 백신 엔진과 DB 상태 동기화는 후속 운영 보강 범위로 둔다.
- 애플리케이션과 Lambda의 S3 IAM 권한은 업로드 object prefix 범위로 제한한다.

기본 후처리 tag:

```text
scan-status
processing-status
metadata-size
metadata-content-type
processor
```

## AWS 배포 실행

이 문서는 AWS 리소스 생성, GitHub Actions 배포, 기능 테스트, 정리 절차를 순서대로 실행하기 위한 런북이다.

실습 비용이 발생하므로 테스트가 끝나면 `terraform destroy`로 정리한다.

### 0. 사전 준비

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

### 1. AWS 로그인 확인

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

### 2. Terraform 변수 파일 작성

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

HTTPS와 도메인 연결을 함께 검증하려면 Route53 public hosted zone을 먼저 준비하고 다음 값을 추가한다.

```hcl
domain_name              = "api.example.com"
route53_hosted_zone_name = "example.com"
```

두 값을 비워두면 Terraform은 ACM/Route53 HTTPS 구성을 건너뛰고 ALB HTTP endpoint를 사용한다.

### 3. Terraform 검증

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
ACM certificate / Route53 records, when domain variables are set
ECR repository
ECS cluster / service / task definition
Aurora PostgreSQL
ElastiCache Redis
S3 bucket
IAM roles / policies
GitHub Actions OIDC provider / deploy role
Lambda file processor
S3 bucket notification
CloudWatch log groups / alarms
ECS Service Auto Scaling
```

### 4. Terraform apply

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
terraform output application_url
terraform output github_actions_deploy_role_arn
terraform output file_processor_function_name
terraform output file_processor_log_group_name
```

이 시점에는 인프라는 생성되었지만 ECR image가 없으면 ECS task가 정상 실행되지 않을 수 있다.

### 5. GitHub Actions secret 등록

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

### 6. GitHub Actions 배포 실행

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
Docker image build / Trivy scan
검증 이미지 artifact 저장 / load
AWS OIDC role assume
ECR login
검증 이미지 tag / ECR push
현재 ECS service task definition 조회
backend container image 교체
새 task definition revision 등록
ECS service rolling deployment
```

배포가 성공하면 ECS service가 새 task를 실행하고 ALB target group health check를 통과한 task로 트래픽을 보낸다.

### 7. AWS 콘솔 확인

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
-> /aws/lambda/fileshare-dev-file-processor

Lambda
-> Functions
-> fileshare-dev-file-processor

CloudWatch
-> Alarms
-> fileshare-dev-file-processor-errors
-> fileshare-dev-file-processor-duration
-> fileshare-dev-backend-cpu-high
-> fileshare-dev-backend-memory-high
-> fileshare-dev-alb-target-5xx
```

정상 기준:

```text
ECR image 존재
ECS service running task 존재
Target group target healthy
CloudWatch log stream 생성
Lambda function 생성
S3 bucket notification 연결
```

### 8. 애플리케이션 URL 확인

Terraform output으로 애플리케이션 URL을 확인한다.

```bash
ALB_DNS=$(terraform output -raw application_alb_dns_name)
BASE_URL=$(terraform output -raw application_url)
echo "$ALB_DNS"
echo "$BASE_URL"
```

Health API를 호출한다.

```bash
curl "$BASE_URL/api/health"
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

### 9. 기능 테스트

테스트 파일을 준비한다.

```bash
printf "hello aws deployment\n" > sample.txt
```

회원가입:

```bash
curl -X POST "$BASE_URL/api/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "aws-user@example.com",
    "password": "password123!",
    "name": "AWS User"
  }'
```

로그인:

```bash
curl -s -X POST "$BASE_URL/api/auth/login" \
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
curl "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TOKEN"
```

파일 업로드:

```bash
curl -X POST "$BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@sample.txt"
```

파일 목록 조회:

```bash
curl "$BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN"
```

응답에서 파일 ID를 확인한 뒤 변수에 넣는다.

```bash
FILE_ID="replace-with-file-id"
```

파일 다운로드:

```bash
curl -L "$BASE_URL/api/files/$FILE_ID/download" \
  -H "Authorization: Bearer $TOKEN" \
  -o downloaded-sample.txt
```

`application_url` output은 도메인/HTTPS 설정이 없으면 ALB HTTP URL이고, 설정되어 있으면 HTTPS URL이다.

파일 삭제:

```bash
curl -X DELETE "$BASE_URL/api/files/$FILE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

### 10. 데이터 확인

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
-> file processor Lambda logs

S3
-> object properties
-> Tags
-> scan-status / processing-status / metadata-size 확인
```

DB에 직접 접속하려면 private subnet 접근 경로가 필요하다. 현재 database subnet은 public 접근을 허용하지 않으므로, 로컬에서 바로 접속하는 구조가 아니다.

Day 11 기준 file processor Lambda는 S3 object tag에 후처리 결과를 기록한다. DB의 `files.status`, `files.scan_status`를 직접 갱신하지 않으므로 API 응답의 scan status와 S3 tag는 아직 별도 확인 대상이다.

### 10-1. 온프레미스/AWS 최종 비교

Day 13 최종 비교 검증은 `docs/design/VALIDATION.md`를 기준으로 수행한다.

핵심 비교 대상:

```text
API 응답 계약
업로드/다운로드 파일 무결성
files.storage_path와 object key 일치 여부
Redis/ElastiCache 캐시 키 형식
ALB target health와 /api/health 응답
CloudWatch backend/Lambda 로그
CloudWatch alarm 상태
```

### 11. 문제 발생 시 확인 순서

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
S3 object prefix 환경변수
실제 object key가 files/ prefix 아래에 있는지 확인
```

HTTPS/도메인 연결 실패:

```text
Route53 public hosted zone 이름 확인
ACM certificate validation status 확인
Route53 validation CNAME record 생성 여부 확인
ALB 443 listener와 certificate ARN 연결 확인
HTTP 요청이 HTTPS로 301 redirect 되는지 확인
```

Lambda 후처리 실패:

```text
Lambda
-> Monitor
-> CloudWatch Logs
-> /aws/lambda/fileshare-dev-file-processor

CloudWatch
-> Alarms
-> fileshare-dev-file-processor-errors

S3 bucket notification prefix가 실제 object key와 일치하는지 확인
Lambda execution role의 s3:GetObject, s3:PutObjectTagging 권한 확인
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

### 12. 정리

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

dev ECR repository는 Terraform에서 `force_delete = true`로 설정되어 있어 repository 삭제 시 남아 있는 image도 함께 삭제된다. 운영 환경에서는 image 보존 정책을 먼저 정하고 `force_delete` 사용 여부를 별도로 검토한다.

### 13. 비용 주의

1시간 실습도 비용이 발생한다.

비용 발생 리소스:

```text
NAT Gateway
ALB
ECS Fargate
Aurora Serverless v2
ElastiCache Redis
CloudWatch Logs
CloudWatch Alarms
Lambda invocation
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
