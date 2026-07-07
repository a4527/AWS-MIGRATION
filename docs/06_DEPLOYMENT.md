# 06. Deployment

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
- JWT secret은 ECS task secret 또는 AWS Secrets Manager로 주입한다.

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
GitHub push
  -> backend test only
```

배포 흐름:

```text
workflow_dispatch
  -> backend test
  -> AWS OIDC role assume
  -> ECR login
  -> Docker image build
  -> ECR push
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

Workflow는 Terraform apply를 실행하지 않는다. 따라서 VPC, ECR, ECS, Aurora, Redis, S3 같은 리소스 생성은 별도 Terraform 단계에서 수행한다. 리소스 생성을 미루는 동안 `main` push는 테스트만 수행하고, ECR push와 ECS 배포는 수동 실행 시에만 진행한다.

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
