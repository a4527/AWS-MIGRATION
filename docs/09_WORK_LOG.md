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

### 예정 작업

- VPC 설계
- EKS, ECR, IAM, ALB, Route53, ACM 범위 정의
- Terraform 프로젝트 구조 생성
- 환경별 변수 체계 설계

## Day 9 - EKS 및 데이터/스토리지 이전

### 예정 작업

- EKS 클러스터 배포
- Aurora PostgreSQL 구성
- ElastiCache 계획 반영
- S3 업로드 구조로 전환
- Secrets 및 IAM 정책 정리

## Day 10 - Kubernetes 배포 및 CI/CD

### 예정 작업

- Kubernetes 매니페스트 작성
- GitHub Actions CI/CD 구축
- ECR 빌드/푸시 연동
- EKS 배포 자동화

## Day 11 - Lambda, 모니터링, 스케일링

### 예정 작업

- S3 이벤트 기반 Lambda 구성
- 바이러스 검사 및 메타데이터 추출 후처리 구현
- CloudWatch 로그/지표 설정
- Auto Scaling 정책 구성
- 헬스체크 및 롤링 배포 확인

## Day 12 - HTTPS 및 보안 강화

### 예정 작업

- Route53 도메인 연결
- ACM 인증서 적용
- ALB HTTPS 구성
- IAM 최소 권한 원칙 점검
- 공개/비공개 리소스 분리 확인

## Day 13 - 최종 검증 및 성능 점검

### 예정 작업

- 온프레미스 vs AWS 동작 비교
- 업로드/다운로드 시나리오 테스트
- 장애 복구 시나리오 테스트
- 로그 및 알람 확인

## Day 14 - 포트폴리오 및 마감

### 예정 작업

- README 정리
- 프로젝트 스토리 작성
- 기술 선택 이유 정리
- 결과 이미지/다이어그램 정리
- `docs/08_PORTFOLIO.md` 완성
