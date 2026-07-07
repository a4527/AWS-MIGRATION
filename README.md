# Enterprise File Sharing Platform Modernization

온프레미스 파일 공유 시스템을 먼저 구현한 뒤, 같은 API 계약과 데이터 모델을 유지하면서 AWS 클라우드 네이티브 구조로 전환한 2주 마이그레이션 프로젝트입니다.

핵심은 단순 배포가 아니라 **애플리케이션 내부 구조, 로컬 운영 환경, AWS 운영 구조를 단계적으로 분리하고 연결한 것**입니다.

![AWS Architecture](docs/assets/portfolio-aws-architecture.png)

## 프로젝트 의의

- Spring Boot API 서버의 요청 처리 흐름과 계층 구조를 명확히 정리했습니다.
- Docker Compose로 PostgreSQL, Redis, MinIO, Nginx를 포함한 온프레미스 실행 환경을 구성했습니다.
- Terraform으로 AWS 네트워크, ECS, Aurora, ElastiCache, S3, Lambda, CloudWatch, IAM을 코드화했습니다.
- 파일 바이너리는 S3에 저장하고, Aurora에는 `storage_path = S3 object key` 형태의 메타데이터만 저장하도록 역할을 분리했습니다.
- S3 ObjectCreated 이벤트 기반 Lambda 후처리로 파일 저장과 후처리 책임을 분리했습니다.
- CloudWatch Logs/Alarms와 ECS 지표를 통해 장애 원인 추적 기준을 문서화했습니다.

## 전체 구조

### 1. Spring Boot 내부 구조

![Spring Boot Flow](docs/assets/portfolio-springboot-flow.png)

Spring Boot 애플리케이션은 요청 수신부터 응답 반환까지 다음 흐름으로 동작합니다.

- `SecurityFilterChain`에서 JWT 인증을 검증합니다.
- Controller는 HTTP 요청/응답 계약을 담당합니다.
- Service 계층은 인증, 사용자, 파일 비즈니스 로직을 처리합니다.
- Domain 계층은 핵심 규칙과 상태를 표현합니다.
- Infrastructure 계층은 JPA, Redis, MinIO/S3 같은 외부 시스템 연동을 담당합니다.

이 구조 덕분에 MinIO에서 S3로, PostgreSQL에서 Aurora로 전환해도 Controller와 API 응답 계약을 유지할 수 있습니다.

### 2. 온프레미스 Docker Compose 구조

![Docker Compose Architecture](docs/assets/portfolio-docker-compose.png)

온프레미스 환경은 Docker Compose 하나로 실행됩니다.

```text
Client
  -> Nginx
  -> Spring Boot Backend
  -> PostgreSQL
  -> Redis
  -> MinIO
```

- Nginx는 외부 요청 진입점입니다.
- Backend는 인증, 권한, 파일 API를 처리합니다.
- PostgreSQL은 사용자와 파일 메타데이터를 저장합니다.
- Redis는 파일 메타데이터 캐시를 저장합니다.
- MinIO는 실제 파일 바이너리를 저장합니다.

### 3. AWS 마이그레이션 구조

```text
Client
  -> Route53
  -> ALB HTTPS Listener
  -> ECS Fargate Backend
      -> Aurora PostgreSQL: metadata, storage_path
      -> ElastiCache Redis: cache
      -> S3: binary files, object tags
  -> S3 ObjectCreated Event
  -> Lambda file processor
  -> CloudWatch Logs / Alarms
```

AWS에서는 public, private, database subnet을 분리했습니다.

| 영역 | 배치 |
| --- | --- |
| Public Subnets | ALB, NAT Gateway |
| Private Subnets | ECS Fargate backend task |
| Database Subnets | Aurora PostgreSQL, ElastiCache Redis |
| AWS Regional Services | S3, Lambda, CloudWatch, ECR, IAM, Secrets Manager |

S3는 subnet 내부 리소스가 아니며, ECS는 S3 HTTPS API를 호출합니다. 현재 Terraform 기준으로 S3 VPC Endpoint가 없으므로 private subnet의 ECS는 NAT Gateway를 통해 S3 regional endpoint에 접근합니다.

## 주요 기능

- 회원가입, 로그인, JWT 기반 인증
- 사용자 조회 및 프로필 수정
- 파일 업로드, 목록 조회, 단건 조회, 다운로드, 논리 삭제
- 파일 확장자/크기 검증
- PostgreSQL/JPA 기반 메타데이터 저장
- Redis 기반 파일 메타데이터 캐시
- MinIO/S3 교체 가능한 파일 저장소 계층
- S3 이벤트 기반 Lambda 후처리
- Terraform 기반 AWS 인프라 구성
- GitHub Actions OIDC 기반 ECR/ECS 배포 흐름
- CloudWatch 로그, 알람, ECS Auto Scaling 기준

## 마이그레이션 매핑

| 온프레미스 | AWS | 유지한 계약 |
| --- | --- | --- |
| Nginx | ALB, Route53, ACM | `/api/**`, `/actuator/health` |
| Spring Boot container | ECS Fargate task | API 요청/응답 형식 |
| PostgreSQL | Aurora PostgreSQL | `users`, `files` 테이블 |
| Redis | ElastiCache Redis | `files:metadata:{fileId}` |
| MinIO | S3 | `files.storage_path = S3 object key` |
| 수동 로그 확인 | CloudWatch Logs/Alarms | 장애 추적 기준 |
| 수동 컨테이너 복구 | ECS service scheduler | health check 기반 복구 |

## 파일 저장 설계

파일 업로드 시 Aurora, ElastiCache, S3는 서로를 거쳐가는 구조가 아니라 ECS 애플리케이션이 각각 직접 접근합니다.

```text
ECS Backend
  -> Aurora PostgreSQL: file metadata, storage_path = S3 object key
  -> ElastiCache Redis: metadata cache
  -> S3: actual file bytes
```

다운로드 시에는 Aurora 또는 Redis에서 `storage_path`를 찾고, 그 값을 S3 object key로 사용해 파일을 읽습니다.

## 실행

온프레미스 통합 실행:

```bash
docker compose up --build
```

API 진입점:

```text
http://localhost:8080
```

MinIO 콘솔:

```text
http://localhost:9001
```

## 검증 기준

- 온프레미스와 AWS의 API 응답 계약이 동일합니다.
- 업로드, 다운로드, 삭제 시나리오가 양쪽 환경에서 성공해야 합니다.
- Aurora의 `files.storage_path`와 S3 object key가 일치해야 합니다.
- Redis/ElastiCache 캐시 키는 `files:metadata:{fileId}` 형식을 유지해야 합니다.
- S3 object 생성 후 Lambda가 실행되고, S3 object tag와 CloudWatch log에 결과가 남아야 합니다.
- ECS task 장애, Lambda 오류, ALB 5xx를 CloudWatch와 AWS console에서 추적할 수 있어야 합니다.

## 문서

- `docs/00_REQUIREMENTS.md`: 요구사항
- `docs/02_ARCHITECTURE.md`: 온프레미스/AWS 아키텍처
- `docs/04_API_SPEC.md`: API 명세
- `docs/05_MIGRATION.md`: 마이그레이션 전략
- `docs/06_DEPLOYMENT.md`: 배포 문서
- `docs/08_PORTFOLIO.md`: 포트폴리오 설명
- `docs/11_ONPREM_MANUAL_TEST.md`: 온프레미스 수동 검증
- `docs/12_TERRAFORM_AWS_INFRA_SUMMARY.md`: Terraform AWS 인프라 요약
- `docs/13_AWS_DEPLOYMENT_RUNBOOK.md`: AWS 배포 런북
- `docs/14_FINAL_VALIDATION.md`: 최종 검증

## 포트폴리오 이미지

- `docs/assets/portfolio-springboot-flow.png`
- `docs/assets/portfolio-docker-compose.png`
- `docs/assets/portfolio-aws-architecture.png`
