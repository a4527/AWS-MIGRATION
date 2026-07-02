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
- MinIO 연결값은 `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` 환경변수로 주입한다.
- Day 6 이후 PostgreSQL, Redis, MinIO, Nginx는 Docker Compose로 통합한다.

예시:

```bash
cd backend
SPRING_PROFILES_ACTIVE=postgres,redis,minio ./gradlew bootRun
```

## 온프레미스

- Dockerfile 작성
- Docker Compose로 통합 실행
- Nginx reverse proxy 적용
- 환경변수와 시크릿 분리
- JWT secret은 로컬 설정 파일에 고정하지 않고 환경변수 또는 compose secret으로 주입한다.

## AWS

- Terraform으로 인프라 생성
- EKS에 애플리케이션 배포
- ECR로 이미지 저장
- ALB로 외부 트래픽 수신
- ACM으로 HTTPS 적용
- Route53으로 도메인 연결
- Lambda 함수 배포 및 이벤트 연결
- S3 이벤트 알림 설정
- Lambda 실행 역할(IAM) 분리
- CloudWatch 로그와 알람 구성
- 바이러스 검사 및 메타데이터 추출 후처리 배포
- JWT secret은 Kubernetes Secret 또는 AWS Secrets Manager로 주입한다.

## CI/CD

- GitHub Actions에서 빌드
- 테스트 실행
- ECR 푸시
- Kubernetes 배포
- Lambda 배포 자동화

## 서버리스 배포 기준

- Lambda는 S3 이벤트와 연결되어야 한다.
- Lambda 실행 권한은 최소 권한 원칙으로 분리한다.
- 서버리스 로그는 CloudWatch에서 확인 가능해야 한다.
- 서버리스 배포는 애플리케이션 배포와 독립적으로 롤백 가능해야 한다.
