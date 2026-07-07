# 09. Work Log

Day별 실제 작업 내역, 산출물, 검증 결과를 기록한다. `TASKS.md`는 체크리스트로 유지하고, 이 문서는 작업 히스토리 추적에 사용한다.

## Day 1 - 요구사항 및 설계

### 완료 작업

- 서비스 목표와 범위를 정의했다.
- 사용자 역할과 권한 모델 초안을 정리했다.
- 파일 라이프사이클과 상태값을 정의했다.
- API 공통 응답 형식과 인증 방식 초안을 작성했다.
- PostgreSQL 기준 핵심 테이블과 인덱스 초안을 작성했다.
- 온프레미스와 AWS 목표 아키텍처를 분리해 정리했다.

### 산출물

- `docs/00_REQUIREMENTS.md`
- `docs/01_ROADMAP.md`
- `docs/02_ARCHITECTURE.md`
- `docs/03_DATABASE.md`
- `docs/04_API_SPEC.md`
- `docs/05_MIGRATION.md`
- `docs/06_DEPLOYMENT.md`
- `docs/07_TROUBLESHOOTING.md`
- `docs/08_PORTFOLIO.md`
- `TASKS.md`

### 결정 사항

- 파일 바이너리는 DB에 저장하지 않고 외부 object storage에 저장한다.
- DB에는 파일 메타데이터만 저장한다.
- 삭제는 논리 삭제를 기본으로 한다.
- 온프레미스 구현과 AWS 구현은 문서에서 명확히 구분한다.
- MinIO에서 S3로 전환할 수 있도록 저장소 접근 계층을 분리한다.

### 검증

- 문서 간 주요 개념을 맞췄다.
- 요구사항, 아키텍처, DB, API 문서의 파일 상태값 기준을 통일했다.

## Day 2 - Spring Boot 프로젝트 골격

### 완료 작업

- Spring Boot 백엔드 프로젝트 골격을 생성했다.
- Gradle 기반 빌드 구성을 추가했다.
- 정식 Gradle Wrapper를 구성했다.
- 패키지 구조를 `auth`, `user`, `file`, `common`, `config`, `infra` 기준으로 분리했다.
- 공통 API 응답 포맷 `ApiResponse`를 추가했다.
- 공통 에러 코드 `ErrorCode`를 추가했다.
- 비즈니스 예외 `BusinessException`과 전역 예외 처리 `GlobalExceptionHandler`를 추가했다.
- Spring Security stateless 설정을 추가했다.
- JWT 인증 필터와 토큰 provider 골격을 추가했다.
- 인증 실패와 권한 부족 응답을 JSON 형식으로 통일했다.
- 기본 상태 확인 API `GET /api/health`를 추가했다.
- Day 2 완료 상태를 `TASKS.md`에 반영했다.
- API, 배포, 마이그레이션 문서를 Day 2 구현 기준에 맞게 갱신했다.

### 산출물

- `backend/build.gradle`
- `backend/settings.gradle`
- `backend/gradlew`
- `backend/gradlew.bat`
- `backend/gradle/wrapper/gradle-wrapper.jar`
- `backend/gradle/wrapper/gradle-wrapper.properties`
- `backend/src/main/java/com/example/fileshare/FileShareApiApplication.java`
- `backend/src/main/java/com/example/fileshare/common/api/ApiResponse.java`
- `backend/src/main/java/com/example/fileshare/common/api/HealthController.java`
- `backend/src/main/java/com/example/fileshare/common/error/ErrorCode.java`
- `backend/src/main/java/com/example/fileshare/common/exception/BusinessException.java`
- `backend/src/main/java/com/example/fileshare/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/example/fileshare/config/security/SecurityConfig.java`
- `backend/src/main/java/com/example/fileshare/config/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/example/fileshare/config/security/JwtTokenProvider.java`
- `backend/src/main/java/com/example/fileshare/config/security/AuthUserPrincipal.java`
- `backend/src/main/java/com/example/fileshare/config/security/RestAuthenticationEntryPoint.java`
- `backend/src/main/java/com/example/fileshare/config/security/RestAccessDeniedHandler.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/java/com/example/fileshare/FileShareApiApplicationTests.java`

### 결정 사항

- 빌드는 Gradle을 기준으로 한다.
- 팀/CI 환경의 Gradle 버전 차이를 막기 위해 정식 Gradle Wrapper를 사용한다.
- Java 버전은 17로 고정한다.
- API 응답은 `/api/**` 기준으로 `ApiResponse` 형식을 사용한다.
- 인증은 stateless JWT 기준으로 설계한다.
- Day 2의 `JwtTokenProvider`는 실제 검증을 구현하지 않고 골격만 둔다.
- 인증이 필요한 API는 Day 3 전까지 `AUTH_INVALID_TOKEN`이 발생하는 것이 정상이다.

### 검증

```bash
cd backend
./gradlew -version
./gradlew test
```

검증 결과:

```text
Gradle 8.10.2
BUILD SUCCESSFUL
```

### 남은 범위

- 회원가입 구현
- 로그인 구현
- JWT 발급과 검증 구현
- 사용자 조회 API 구현
- 권한 기반 접근 제어 적용

## Day 3 - 인증 및 사용자 관리

### 완료 작업

- 회원가입 구현
- 로그인 구현
- JWT 발급/검증 구현
- 사용자 조회 API 구현
- 권한 기반 접근 제어 적용
- 현재 사용자 프로필 수정 API 구현
- 인메모리 사용자 저장소 구현
- 사용자 도메인 모델과 Repository 인터페이스 분리
- Spring Security method security 적용
- 인증/권한 API 통합 테스트 작성
- Day 3 완료 상태를 `TASKS.md`에 반영했다.
- API, 배포, 마이그레이션 문서를 Day 3 구현 기준에 맞게 갱신했다.

### 산출물

- `backend/src/main/java/com/example/fileshare/auth/api/AuthController.java`
- `backend/src/main/java/com/example/fileshare/auth/api/SignupRequest.java`
- `backend/src/main/java/com/example/fileshare/auth/api/LoginRequest.java`
- `backend/src/main/java/com/example/fileshare/auth/api/LoginResponse.java`
- `backend/src/main/java/com/example/fileshare/auth/application/AuthService.java`
- `backend/src/main/java/com/example/fileshare/user/domain/User.java`
- `backend/src/main/java/com/example/fileshare/user/domain/UserRole.java`
- `backend/src/main/java/com/example/fileshare/user/repository/UserRepository.java`
- `backend/src/main/java/com/example/fileshare/user/repository/InMemoryUserRepository.java`
- `backend/src/main/java/com/example/fileshare/user/api/UserController.java`
- `backend/src/main/java/com/example/fileshare/user/api/UserResponse.java`
- `backend/src/main/java/com/example/fileshare/user/api/UpdateMeRequest.java`
- `backend/src/main/java/com/example/fileshare/config/security/JwtTokenProvider.java`
- `backend/src/test/java/com/example/fileshare/auth/AuthApiIntegrationTests.java`

### 결정 사항

- Day 3은 Day 5의 PostgreSQL/JPA 연동 전 단계이므로 사용자 저장소를 인메모리로 구현한다.
- `UserRepository` 인터페이스를 먼저 두어 Day 5에 JPA Repository로 교체할 수 있게 한다.
- JWT는 stateless access token만 구현한다.
- 토큰 서명은 HS256 기준으로 구현하고, 로컬 secret과 만료 시간은 `application.yml`에서 관리한다.
- `GET /api/users/{id}`는 `ADMIN` 역할만 접근 가능하게 한다.
- Refresh token과 logout의 실제 무효화 저장소는 Redis 연동 이후 구현한다.

### 검증

```bash
cd backend
./gradlew test
```

검증 결과:

```text
BUILD SUCCESSFUL
```

## Day 4 - 파일 CRUD

### 완료 작업

- 업로드 API 구현
- 다운로드 API 구현
- 삭제 API 구현
- 메타데이터 저장 로직 구현
- 파일 확장자/크기 검증
- 파일 메타데이터 도메인 모델과 상태값 구현
- 파일 메타데이터 Repository 인터페이스와 인메모리 구현 추가
- 파일 저장소 `FileStorage` 인터페이스와 로컬 파일 시스템 구현 추가
- 소유자 및 관리자 기준 파일 접근 제어 구현
- 파일 API 통합 테스트 작성
- Day 4 완료 상태를 `TASKS.md`에 반영했다.
- API, 배포, 마이그레이션 문서를 Day 4 구현 기준에 맞게 갱신했다.

### 산출물

- `backend/src/main/java/com/example/fileshare/file/domain/FileMetadata.java`
- `backend/src/main/java/com/example/fileshare/file/domain/FileStatus.java`
- `backend/src/main/java/com/example/fileshare/file/domain/ScanStatus.java`
- `backend/src/main/java/com/example/fileshare/file/repository/FileMetadataRepository.java`
- `backend/src/main/java/com/example/fileshare/file/repository/InMemoryFileMetadataRepository.java`
- `backend/src/main/java/com/example/fileshare/file/application/FileService.java`
- `backend/src/main/java/com/example/fileshare/file/application/FileDownload.java`
- `backend/src/main/java/com/example/fileshare/file/api/FileController.java`
- `backend/src/main/java/com/example/fileshare/file/api/FileResponse.java`
- `backend/src/main/java/com/example/fileshare/file/api/FileDeleteResponse.java`
- `backend/src/main/java/com/example/fileshare/infra/storage/FileStorage.java`
- `backend/src/main/java/com/example/fileshare/infra/storage/LocalFileStorage.java`
- `backend/src/main/java/com/example/fileshare/infra/storage/StoredFile.java`
- `backend/src/main/java/com/example/fileshare/infra/storage/StorageObject.java`
- `backend/src/test/java/com/example/fileshare/file/FileApiIntegrationTests.java`

### 결정 사항

- Day 4는 Day 5 PostgreSQL/MinIO 연동 전 단계이므로 파일 메타데이터와 바이너리 저장소를 각각 인메모리/로컬 파일 시스템으로 구현한다.
- 저장소 교체를 위해 서비스 계층은 `FileMetadataRepository`와 `FileStorage` 인터페이스만 의존한다.
- 바이러스 검사와 메타데이터 추출 후처리는 아직 없으므로 업로드 직후 `available/CLEAN` 상태로 등록한다.
- 삭제는 물리 삭제가 아니라 `deleted` 상태로 전환하는 논리 삭제로 처리한다.
- 일반 사용자는 본인 파일만 조회/다운로드/삭제할 수 있고, `ADMIN`은 다른 사용자의 파일 메타데이터와 다운로드에 접근할 수 있다.
- 기본 업로드 제한은 10MiB, 허용 확장자는 `pdf`, `png`, `jpg`, `jpeg`, `txt`, `csv`로 둔다.

### 검증

```bash
cd backend
./gradlew test
```

검증 결과:

```text
BUILD SUCCESSFUL
```

## Day 5 - PostgreSQL, Redis, MinIO 연동

### 완료 작업

- PostgreSQL 테이블 확정
- JPA 엔티티 및 Repository 작성
- Redis 캐시 적용
- MinIO 연동
- 로컬 파일 저장소 대체 확인
- Spring Data JPA, PostgreSQL driver, Redis, MinIO 의존성 추가
- `postgres`, `redis`, `minio` 프로필 설정 파일 추가
- 사용자 JPA 엔티티와 Repository 어댑터 구현
- 파일 메타데이터 JPA 엔티티와 Repository 어댑터 구현
- 서비스 계층 ID 생성을 저장소 계층으로 이동해 JPA generated identity를 사용할 수 있게 정리
- Redis 기반 파일 단건 메타데이터 캐시 구현
- MinIO 기반 `FileStorage` 구현
- JPA 프로필 통합 테스트 작성
- Day 5 완료 상태를 `TASKS.md`에 반영했다.
- 데이터베이스, 배포, 마이그레이션, README 문서를 Day 5 구현 기준에 맞게 갱신했다.

### 산출물

- `backend/src/main/resources/application-postgres.yml`
- `backend/src/main/resources/application-redis.yml`
- `backend/src/main/resources/application-minio.yml`
- `backend/src/main/java/com/example/fileshare/user/repository/jpa/UserEntity.java`
- `backend/src/main/java/com/example/fileshare/user/repository/jpa/SpringDataUserJpaRepository.java`
- `backend/src/main/java/com/example/fileshare/user/repository/jpa/JpaUserRepositoryAdapter.java`
- `backend/src/main/java/com/example/fileshare/file/repository/jpa/FileMetadataEntity.java`
- `backend/src/main/java/com/example/fileshare/file/repository/jpa/SpringDataFileMetadataJpaRepository.java`
- `backend/src/main/java/com/example/fileshare/file/repository/jpa/JpaFileMetadataRepositoryAdapter.java`
- `backend/src/main/java/com/example/fileshare/file/application/FileMetadataCache.java`
- `backend/src/main/java/com/example/fileshare/file/application/NoOpFileMetadataCache.java`
- `backend/src/main/java/com/example/fileshare/file/application/RedisFileMetadataCache.java`
- `backend/src/main/java/com/example/fileshare/infra/storage/MinioFileStorage.java`
- `backend/src/test/java/com/example/fileshare/persistence/PostgresProfileIntegrationTests.java`

### 결정 사항

- 기본 개발 모드는 외부 서비스 없이 실행 가능하게 유지한다.
- PostgreSQL, Redis, MinIO 연동은 각각 `postgres`, `redis`, `minio` Spring profile로 활성화한다.
- JPA 엔티티는 도메인 모델과 분리하고, 어댑터가 도메인 Repository 인터페이스를 구현한다.
- 파일 단건 메타데이터 캐시는 Redis에 `files:metadata:{fileId}` 키로 저장하며 TTL은 5분으로 둔다.
- MinIO 저장소의 `storagePath`는 object key로 저장한다.
- Day 5에서는 `users`, `files` 엔티티를 구현하고, `file_permissions`, `upload_logs`는 후속 기능 구현 시 확장한다.

### 검증

```bash
cd backend
./gradlew test
```

검증 결과:

```text
BUILD SUCCESSFUL
```

## Day 6 - Docker Compose 및 Nginx

### 완료 작업

- Dockerfile 작성
- Docker Compose 구성
- Nginx reverse proxy 설정
- 환경변수 분리
- Docker Compose 파일 YAML 구조 검증
- Docker Compose 기반 로컬 통합 실행 검증
- Nginx를 통한 `/api/health`, `/actuator/health` 응답 검증
- 애플리케이션 설정의 JWT, 서버 포트, 파일 업로드 제한, 로컬 저장 경로를 환경변수로 주입 가능하게 변경
- Day 6 완료 상태를 `TASKS.md`에 반영했다.
- 배포, 아키텍처, 마이그레이션 문서를 Day 6 구현 기준에 맞게 갱신했다.

### 산출물

- `backend/Dockerfile`
- `backend/.dockerignore`
- `.dockerignore`
- `docker-compose.yml`
- `.env.example`
- `nginx/conf.d/default.conf`

### 결정 사항

- 온프레미스 통합 실행은 Docker Compose를 기준으로 한다.
- 외부 API 진입점은 Nginx이며 기본 호스트 포트는 `8080`이다.
- 백엔드 컨테이너는 `postgres,redis,minio` 프로필을 활성화한다.
- PostgreSQL과 Redis는 healthcheck를 통해 준비 상태를 확인한 뒤 백엔드를 시작한다.
- MinIO 버킷은 기존 `MinioFileStorage`가 업로드 시점에 생성한다.
- Compose 기본 환경변수는 개발용이며 운영에서는 `.env`, compose secret, 또는 배포 환경의 secret 저장소로 교체한다.

### 검증

```bash
cd backend
./gradlew test
./gradlew bootJar

cd ..
docker compose config
ruby -e 'require "yaml"; YAML.load_file("docker-compose.yml"); puts "YAML OK"'
docker compose up --build -d
docker compose ps
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/actuator/health
```

검증 결과:

```text
BUILD SUCCESSFUL
bootJar BUILD SUCCESSFUL
YAML OK
docker compose config 성공
backend image build 성공
postgres, redis, minio, backend, nginx 컨테이너 기동 성공
GET /api/health 200 OK
GET /actuator/health 200 OK
```

## Day 7 - 온프레미스 검증 및 문서화

### 완료 작업

- 기능 테스트
- 장애 시나리오 점검
- API 문서 정리
- 트러블슈팅 초안 작성
- 온프레미스 아키텍처 문서 마무리

### 산출물

- `docs/11_ONPREM_MANUAL_TEST.md`
- `docs/04_API_SPEC.md`
- `docs/02_ARCHITECTURE.md`
- `docs/05_MIGRATION.md`
- `docs/07_TROUBLESHOOTING.md`
- `TASKS.md`

### 결정 사항

- Day 7 검증은 자동화 스크립트가 아니라 사용자가 직접 실행하는 수동 체크리스트 기반으로 진행한다.
- 기능 테스트는 Nginx 진입점 `localhost:8080`을 기준으로 수행한다.
- 저장소 검증은 PostgreSQL 테이블, Redis 캐시 키, MinIO 버킷/object를 각각 직접 확인하는 방식으로 둔다.
- 장애 시나리오는 `docs/11_ONPREM_MANUAL_TEST.md`에 수동 점검 절차로 정리한다.
- Redis는 캐시 계층이지만 현재 구현에서는 Redis 장애가 API 오류로 이어질 수 있으므로 운영 개선 후보로 기록한다.

### 검증

사용자 수동 기능 테스트 완료.

검증 범위:

- Compose 기반 서비스 기동
- Health API
- 회원가입과 로그인
- JWT 기반 사용자 조회와 프로필 수정
- 파일 업로드, 목록 조회, 단건 조회, 다운로드, 삭제
- PostgreSQL 사용자/파일 메타데이터 확인
- Redis 파일 메타데이터 캐시 확인
- MinIO 버킷과 object 확인
- 인증 누락, 확장자 제한, 권한 부족 실패 케이스 확인

## Day 8 - AWS 인프라 설계 및 Terraform 시작

### 완료 작업

- VPC 설계
- ECS, ECR, IAM, ALB, Route53, ACM 범위 정의
- Terraform 프로젝트 구조 생성
- 환경별 변수 체계 설계
- Terraform `dev` 환경 파일 작성
- VPC, ECR, S3, IAM 기본 모듈 작성
- Terraform state, tfvars, provider lock 파일 관련 ignore 규칙 보강
- 아키텍처, 배포, 마이그레이션 문서를 Day 8 설계 기준으로 갱신
- Day 8 완료 상태를 `TASKS.md`에 반영했다.

### 산출물

- `terraform/environments/dev/main.tf`
- `terraform/environments/dev/providers.tf`
- `terraform/environments/dev/variables.tf`
- `terraform/environments/dev/outputs.tf`
- `terraform/environments/dev/terraform.tfvars.example`
- `terraform/environments/dev/backend.tf.example`
- `terraform/modules/vpc/*`
- `terraform/modules/ecr/*`
- `terraform/modules/s3/*`
- `terraform/modules/iam/*`
- `terraform/README.md`
- `docs/02_ARCHITECTURE.md`
- `docs/05_MIGRATION.md`
- `docs/06_DEPLOYMENT.md`
- `TASKS.md`

### 결정 사항

- 개발 환경 기본 리전은 `ap-northeast-2`로 둔다.
- 개발 VPC CIDR은 `10.20.0.0/16`으로 둔다.
- Public subnet은 ALB와 NAT Gateway 용도로 둔다.
- Private subnet은 ECS Fargate task 용도로 둔다.
- Database subnet은 Aurora PostgreSQL과 ElastiCache Redis 용도로 둔다.
- 개발 비용 절감을 위해 Terraform 초안은 NAT Gateway 1개로 시작한다.
- S3는 public access block, SSE-S3 암호화, versioning, multipart upload 정리 정책을 기본으로 둔다.
- ECR은 backend repository와 최근 이미지 20개 보관 lifecycle policy를 둔다.
- 애플리케이션 IAM은 Day 8에서 S3 접근 policy만 정의하고, Day 9 ECS task role 연결로 확장한다.
- Route53, ACM, ALB HTTPS, ECS, Aurora, ElastiCache, Lambda, CloudWatch는 Day 9 이후 모듈 확장 대상으로 둔다.

### 검증

```bash
terraform fmt -recursive terraform
terraform -chdir=terraform/environments/dev init -backend=false
terraform -chdir=terraform/environments/dev validate
git diff --check
```

검증 결과:

```text
git diff --check 통과
Terraform v1.15.7 설치 확인
terraform fmt -recursive terraform 성공
terraform init -backend=false 성공
terraform validate 성공
```

## Day 9 - ECS 및 데이터/스토리지 이전

### 완료 작업

- Terraform `security-groups` 모듈 추가
- Terraform `ecs` 모듈 추가
- Terraform `aurora` 모듈 추가
- Terraform `elasticache` 모듈 추가
- dev 환경에서 ECS, Aurora, ElastiCache, SG, task role 연결
- ECS task execution role과 애플리케이션 task role 정의
- 기존 S3 접근 IAM policy를 ECS task role에 attach하도록 연결
- Aurora PostgreSQL Serverless v2 cluster와 database subnet group 정의
- ElastiCache Redis replication group과 subnet group 정의
- Spring Boot `s3` profile과 `S3FileStorage` 구현 추가
- Redis TLS 연결용 `REDIS_SSL_ENABLED` 설정 추가
- Day 9 변경 사항을 아키텍처, 마이그레이션, 배포 문서에 반영

### 결정 사항

- ALB는 ECS 모듈에서 생성하고 public subnet에 배치한다.
- ECS target group은 `/api/health`를 health check path로 사용한다.
- 애플리케이션의 S3 접근은 access key가 아니라 ECS task role로 연결한다.
- Aurora와 Redis는 database subnet에 두고 application SG에서 오는 트래픽만 허용한다.
- AWS 배포 profile은 `postgres,redis,s3` 조합을 기준으로 한다.

### 검증

```bash
terraform fmt -recursive terraform
terraform -chdir=terraform/environments/dev init -backend=false
terraform -chdir=terraform/environments/dev validate
cd backend && ./gradlew test
```

검증 결과:

```text
terraform fmt -recursive terraform 성공
terraform init -backend=false 성공
terraform validate 성공
./gradlew test 성공
```

### 추가 문서화

- `docs/12_TERRAFORM_AWS_INFRA_SUMMARY.md`에 Day 8~Day 9 기준 Terraform 기반 AWS 인프라 설계를 1차 정리했다.

## Day 10 - ECS 배포 및 CI/CD

### 완료 작업

- ECS task definition 배포 흐름 작성
- GitHub Actions CI/CD 구축
- ECR 빌드/푸시 연동
- ECS service 배포 자동화
- GitHub Actions OIDC provider와 deploy role Terraform 코드 추가

### 산출물

- `.github/workflows/backend-ci-cd.yml`
- `terraform/modules/github-actions/*`
- `docs/06_DEPLOYMENT.md`
- `docs/05_MIGRATION.md`
- `docs/13_AWS_DEPLOYMENT_RUNBOOK.md`
- `TASKS.md`

### 결정 사항

- 실제 AWS 리소스 생성은 최대한 미루고, Day 10에서는 배포 자동화 정의와 문서를 먼저 준비한다.
- Workflow는 Terraform apply를 실행하지 않는다.
- 리소스 생성을 미루는 동안 `main` push는 backend test만 수행하고, ECR push와 ECS 배포는 수동 실행으로 제한한다.
- ECR repository와 ECS service는 Terraform으로 생성된 이후 존재한다고 가정한다.
- Docker image tag는 `github.sha`를 사용한다.
- ECS 배포는 현재 service의 task definition을 조회하고 backend container image만 교체하는 rolling deployment 방식으로 둔다.
- GitHub Actions의 AWS 인증은 `AWS_DEPLOY_ROLE_ARN` secret과 OIDC assume role을 기준으로 한다.
- Deploy role은 ECR push, ECS service update, task definition register, ECS task role pass 권한만 갖도록 제한한다.

### 검증

```bash
git diff --check
```

## Day 11 - Lambda, 모니터링, 스케일링

### 목표

- S3 이벤트 기반 Lambda 구성
- 바이러스 검사 및 메타데이터 추출 후처리 구현
- CloudWatch 로그/지표 설정
- Auto Scaling 정책 구성
- 헬스체크 및 롤링 배포 확인

### 진행 내용

- `terraform/functions/file-processor/handler.py` Lambda 핸들러 추가
- `terraform/modules/file-processor` 모듈 추가
- S3 `ObjectCreated` 이벤트를 file processor Lambda로 연결
- Lambda 실행 role, 최소 S3 object read/tagging 권한, CloudWatch Logs 권한 구성
- Lambda error/duration CloudWatch alarm 구성
- ECS service Auto Scaling target 추가
- ECS CPU/memory target tracking policy 추가
- ECS CPU/memory alarm과 ALB target 5xx alarm 추가
- ECS/Lambda 로그 보존 기간을 `cloudwatch_log_retention_in_days` 변수로 통일
- Terraform dev output에 file processor function/log group 추가
- 마이그레이션, 배포, Terraform summary, AWS runbook 문서 갱신

### 산출물

- `terraform/functions/file-processor/handler.py`
- `terraform/modules/file-processor/*`
- `terraform/modules/ecs/main.tf`
- `terraform/environments/dev/main.tf`
- `docs/05_MIGRATION.md`
- `docs/06_DEPLOYMENT.md`
- `docs/12_TERRAFORM_AWS_INFRA_SUMMARY.md`
- `docs/13_AWS_DEPLOYMENT_RUNBOOK.md`
- `TASKS.md`

### 결정 사항

- Day 11 Lambda는 실제 백신 엔진 대신 차단 확장자 기반 기본 가드와 metadata 추출을 구현한다.
- Lambda 처리 결과는 S3 object tag에 기록한다.
- DB의 `files.status`, `files.scan_status` 갱신은 후속 연동으로 분리한다.
- Lambda 배포는 별도 GitHub Actions job이 아니라 Terraform `archive` provider 기반 패키징으로 시작한다.
- CloudWatch alarm은 감지 리소스만 생성하고 SNS 같은 알림 action은 후속 운영 보강으로 둔다.
- 개발 기본 Auto Scaling 범위는 `min=2`, `max=4`로 둔다. 비용 절감을 위해 실습 시 `min=1`, `desired=1`로 낮출 수 있다.

### 검증

```bash
terraform fmt -recursive terraform
python3 -m py_compile terraform/functions/file-processor/handler.py
terraform -chdir=terraform/environments/dev validate
```

## Day 12 - HTTPS 및 보안 강화

### 목표

- Route53 도메인 연결
- ACM 인증서 적용
- ALB HTTPS 구성
- IAM 최소 권한 원칙 점검
- 공개/비공개 리소스 분리 확인

### 진행 내용

- dev Terraform 환경에 선택형 Route53/ACM HTTPS 구성을 추가했다.
- `domain_name`과 `route53_hosted_zone_name`이 모두 설정되면 ACM 인증서를 DNS 검증 방식으로 발급한다.
- ALB는 인증서가 있을 때 443 HTTPS listener를 만들고, 80 HTTP listener는 HTTPS로 301 redirect한다.
- 도메인 설정이 없으면 기존처럼 HTTP listener가 target group으로 forward하므로 로컬/실습 Terraform 검증이 가능하다.
- Route53 alias A record를 ALB로 연결해 사용자 요청 진입점을 custom domain으로 전환할 수 있게 했다.
- ECS task role의 S3 object 권한을 전체 bucket에서 `files/` prefix 기준으로 좁혔다.
- file processor Lambda의 S3 object read/tagging 권한도 notification prefix와 같은 prefix로 좁혔다.
- Spring Boot S3 저장소가 `S3_OBJECT_PREFIX`를 사용해 `files/` 아래로 object key를 생성하도록 맞췄다.
- 공개 리소스는 ALB/NAT/IGW, private 리소스는 ECS task, database subnet 리소스는 Aurora/Redis로 문서화했다.

### 산출물

- `terraform/environments/dev/main.tf`
- `terraform/environments/dev/variables.tf`
- `terraform/environments/dev/outputs.tf`
- `terraform/modules/ecs/*`
- `terraform/modules/iam/*`
- `terraform/modules/file-processor/main.tf`
- `backend/src/main/java/com/example/fileshare/infra/storage/S3FileStorage.java`
- `backend/src/main/resources/application-s3.yml`
- `docs/05_MIGRATION.md`
- `docs/06_DEPLOYMENT.md`
- `docs/12_TERRAFORM_AWS_INFRA_SUMMARY.md`
- `docs/13_AWS_DEPLOYMENT_RUNBOOK.md`
- `TASKS.md`

### 결정 사항

- 도메인이 없는 개발 환경을 위해 HTTPS는 조건부로 구성한다.
- ACM 인증서는 ALB와 같은 region의 provider에서 생성한다. 현재 dev region은 `ap-northeast-2`이다.
- 인증서 검증은 Route53 DNS validation으로 자동화한다.
- 애플리케이션 object key prefix와 Lambda notification prefix는 같은 `file_processor_object_prefix` 값을 공유한다.
- 운영 수준의 추가 egress 제한은 S3/ECR/CloudWatch VPC endpoint 설계와 함께 후속 보강으로 둔다.

### 검증

```bash
terraform fmt -recursive terraform
./gradlew test
terraform -chdir=terraform/environments/dev validate
```

## Day 13 - 최종 검증 및 성능 점검

### 목표

- 온프레미스 vs AWS 동작 비교
- 업로드/다운로드 시나리오 테스트
- 장애 복구 시나리오 테스트
- 로그 및 알람 확인

### 진행 내용

- 온프레미스 Docker Compose와 AWS ECS 환경의 최종 비교 기준을 정리했다.
- 공통 API 계약 검증 순서를 health, signup, login, current user, file workflow 기준으로 정의했다.
- 업로드/다운로드 검증에서 원본 파일과 다운로드 파일을 `diff`로 비교하도록 절차를 구체화했다.
- MinIO/S3 object key와 `files.storage_path` 비교 기준을 정리했다.
- Redis/ElastiCache 캐시 키 형식 비교 기준을 정리했다.
- AWS 장애 복구 검증 대상을 ECS rolling deployment, ALB target health, S3 권한, Lambda 후처리로 나누어 정리했다.
- CloudWatch Logs와 CloudWatch Alarm 확인 대상을 Day 11~12 Terraform 구성 기준으로 정리했다.
- 개발 환경에서 수행 가능한 smoke 성능 점검 절차를 추가했다.
- `docs/14_FINAL_VALIDATION.md`의 중복된 AWS 전환 가치 설명을 검증 매트릭스로 줄였다.
- AWS 장애 복구 검증에 ECS task `stop-task` 실패 주입, Lambda malformed event 실패 주입, 로그 확인, 정상 복구 확인 절차를 추가했다.
- 마이그레이션 문서의 Day 13 장애 복구 기준을 실제 실패 주입과 복구 검증 기준에 맞게 갱신했다.
- Day 13 완료 상태를 `TASKS.md`에 반영했다.
- 마이그레이션 문서에 Day 13 최종 검증 기준을 추가했다.

### 산출물

- `docs/14_FINAL_VALIDATION.md`
- `docs/05_MIGRATION.md`
- `docs/09_WORK_LOG.md`
- `docs/13_AWS_DEPLOYMENT_RUNBOOK.md`
- `TASKS.md`

### 결정 사항

- 실제 AWS 리소스 생성과 부하 테스트는 비용이 발생하므로 런북 기반 수동 실행으로 둔다.
- Day 13은 실행 가능한 최종 검증 절차와 완료 판정 기준을 문서화하는 방식으로 완료한다.
- AWS 배포 후 API 경로는 `terraform output -raw application_url`을 기준으로 호출한다.
- 정상 상태의 CloudWatch Alarm은 `OK` 또는 `INSUFFICIENT_DATA`일 수 있으므로, 알람 존재와 장애 시 전환 가능성을 함께 확인한다.
- 본격적인 부하 테스트는 개발 Terraform 기본 용량과 분리해 별도 테스트 계획으로 수행한다.

### 검증

```bash
cd backend
./gradlew test

cd ..
terraform fmt -recursive terraform
terraform -chdir=terraform/environments/dev validate
git diff --check
```

## Day 14 - 포트폴리오 및 마감

### 완료 작업

- README 정리
- 프로젝트 스토리 작성
- 기술 선택 이유 정리
- 결과 이미지/다이어그램 정리
- `docs/08_PORTFOLIO.md` 완성
- Day 14 완료 상태를 `TASKS.md`에 반영했다.
- 마이그레이션 문서에 Day 14 마감 기준을 추가했다.

### 산출물

- `README.md`
- `docs/08_PORTFOLIO.md`
- `docs/05_MIGRATION.md`
- `docs/09_WORK_LOG.md`
- `TASKS.md`

### 결정 사항

- Day 14는 새 기능 구현보다 포트폴리오와 산출물 정리를 완료 범위로 둔다.
- 포트폴리오 설명은 "AWS 서비스를 사용했다"가 아니라 온프레미스와 AWS의 역할 차이, 유지한 API/데이터 계약, 운영 개선 효과를 중심으로 정리한다.
- CloudWatch 지표는 단일 수치만으로 장애를 판단하지 않고 CPU/memory, ALB 5xx, 응답 시간, task 재시작, Lambda error를 함께 보는 기준으로 설명한다.
- 결과 이미지는 온프레미스 실행, AWS 인프라, ECS/ALB 상태, S3/Lambda 후처리, CloudWatch 지표 순서로 정리한다.

### 검증

```bash
git diff --check
```
