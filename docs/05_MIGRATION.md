# 05. Migration

## Migration Strategy

온프레미스에서 구현한 기능과 데이터 계약을 유지한 채, 저장소와 실행 환경만 단계적으로 AWS로 전환한다.

## 전제

- 애플리케이션의 기능 계약은 온프레미스와 AWS에서 동일하게 유지한다.
- 파일 상태와 삭제 정책은 `docs/00_REQUIREMENTS.md` 기준을 따른다.
- DB 스키마와 API 응답 형식은 가능한 한 변경하지 않는다.

### Storage

- MinIO -> Amazon S3
- 업로드 경로와 URL 생성 로직을 추상화
- S3 이벤트 기반 후처리는 Lambda로 분리

### Serverless

- 파일 업로드 후 필요한 비동기 작업인 바이러스 검사와 메타데이터 추출을 Lambda로 분리
- S3 이벤트를 트리거로 사용하는 후처리 구조로 전환
- 서버리스 실행 결과를 CloudWatch에서 추적

### Database

- PostgreSQL -> Amazon Aurora PostgreSQL
- 스키마 호환성 유지
- 마이그레이션 전후 데이터 검증 수행

### Cache

- Redis -> Amazon ElastiCache
- 캐시 키 구조를 유지해 교체 비용을 낮춘다

### Runtime

- Docker Compose -> Amazon EKS
- Spring Boot 이미지를 ECR로 배포
- HPA 또는 오토스케일링 적용

## 전환 순서

1. 애플리케이션 코드에서 저장소 추상화
2. AWS 리소스 준비
3. 테스트 환경에서 S3/Aurora/ElastiCache/Lambda 연결
4. 배포 파이프라인 변경
5. 트래픽 전환
6. 검증 및 최종 안정화

## Day 2 구현 기준

- Spring Boot 백엔드는 온프레미스 구현을 우선하되 패키지 경계를 `infra/storage`로 분리해 MinIO에서 S3로 교체 가능한 구조를 준비한다.
- API 응답 형식과 에러 코드는 온프레미스와 AWS 배포에서 동일하게 유지한다.
- 보안 설정은 stateless JWT 인증을 기준으로 두어 Docker Compose와 EKS 양쪽에서 세션 저장소 의존성을 만들지 않는다.
- `GET /api/health`와 Actuator health endpoint를 유지해 Docker Compose, Kubernetes readiness/liveness probe, ALB health check에서 같은 상태 확인 기준을 사용할 수 있게 한다.

## Day 3 구현 기준

- 사용자 저장소는 Day 5 PostgreSQL 연동 전까지 인메모리 구현을 사용한다.
- `UserRepository` 인터페이스를 기준으로 서비스 계층을 작성해 PostgreSQL/Aurora 전환 시 컨트롤러와 인증 서비스 계약을 유지한다.
- JWT access token은 stateless 구조로 유지해 Docker Compose와 EKS 배포 간 세션 저장소 차이를 만들지 않는다.
- 로컬 `app.jwt.secret`은 개발용 값이며, 운영/AWS 배포에서는 Kubernetes Secret 또는 AWS Secrets Manager로 분리한다.
- Refresh token과 logout token blacklist는 Redis/ElastiCache 연동 이후 저장소 계약을 확정한다.

## Day 4 구현 기준

- 파일 메타데이터는 `FileMetadataRepository` 인터페이스 뒤에 두고, Day 4에서는 인메모리 구현을 사용한다.
- 파일 바이너리 저장은 `FileStorage` 인터페이스 뒤에 두고, Day 4에서는 로컬 파일 시스템 구현을 사용한다.
- 업로드 API의 외부 계약은 저장소 구현과 분리해 유지하므로 Day 5 MinIO, Day 9 S3 전환 시 컨트롤러 계약을 바꾸지 않는다.
- Day 4 로컬 구현은 바이러스 검사와 메타데이터 추출 후처리를 수행하지 않으며, 업로드 직후 `available/CLEAN` 상태로 등록한다.
- AWS 전환 시 `LocalFileStorage`는 S3 기반 구현으로 교체하고 `storagePath`는 S3 object key 또는 URI 계약으로 유지한다.
- 논리 삭제 정책은 온프레미스와 AWS에서 동일하게 유지하고, 실제 object 정리는 별도 배치 또는 lifecycle 정책으로 분리한다.

## Day 5 구현 기준

- `postgres` 프로필에서 사용자와 파일 메타데이터 저장소가 JPA/PostgreSQL 어댑터로 전환된다.
- JPA 엔티티는 도메인 모델과 분리해 두어 API 계층과 서비스 계층의 계약을 유지한다.
- `redis` 프로필에서 파일 단건 메타데이터 캐시가 활성화되며, 캐시 키는 `files:metadata:{fileId}` 형식을 사용한다.
- 업로드와 단건 조회 시 메타데이터 캐시를 갱신하고, 논리 삭제 시 캐시를 제거한다.
- `minio` 프로필에서 `FileStorage` 구현이 로컬 파일 시스템에서 MinIO object storage로 전환된다.
- MinIO의 `storagePath`는 object key로 저장하므로 Day 9 S3 전환 시 S3 object key 계약으로 그대로 이어갈 수 있다.
- AWS 전환 시 PostgreSQL은 Aurora PostgreSQL, Redis는 ElastiCache, MinIO는 S3 구현체로 교체한다.
