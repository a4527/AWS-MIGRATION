# Enterprise File Sharing Platform Modernization

Spring Boot 파일 공유 API를 로컬 Docker Compose(온프레미스)에서 운영 기준선을 만든 뒤, 같은 API·데이터 계약을 유지한 채 Terraform으로 구성한 AWS로 옮기는 프로젝트입니다.

코드(애플리케이션·Compose·Terraform·CI/CD)는 준비되어 있고, 2026-11-17부터 문서를 현재 코드 기준으로 재정비해 **온프레미스 구축 → 컨테이너 실행·검증 → AWS 마이그레이션** 순서로 다시 진행합니다. 진행 상태는 [PROGRESS](docs/PROGRESS.md)를 보세요.

![AWS Architecture](docs/assets/portfolio-aws-architecture.png)

## 구성

| 역할 | 온프레미스 (로컬 WSL2 + Compose) | AWS (Terraform) |
| --- | --- | --- |
| 진입점 | nginx | Route53·ACM(선택)·ALB |
| 애플리케이션 | Spring Boot 컨테이너 | ECS Fargate |
| DB | PostgreSQL 16 | Aurora PostgreSQL |
| 캐시 | Redis 7 | ElastiCache Redis |
| 파일 | MinIO | S3 (`files/` 접두어) |
| 후처리 | 없음 | S3 이벤트 → Lambda |
| 배포 | `docker compose up` | GitHub Actions(OIDC) → ECR → ECS |

구조와 흐름은 [DESIGN](docs/DESIGN.md)과 [아키텍처 페이지](docs/assets/architecture-overview.html)에 있습니다.

## 주요 기능

- 회원가입·로그인(JWT), 사용자 조회·수정, 관리자 전용 사용자 조회
- 파일 업로드(최대 10MiB, 확장자 제한)·목록·단건·다운로드·논리 삭제
- `Idempotency-Key`로 업로드 재전송을 한 번만 처리
- 업로드 실패 시 객체 보상 삭제, 관리자용 고아 객체 점검 API
- Redis 메타데이터 캐시(장애 시 DB 우회, 버전 비교 쓰기)
- MinIO·S3 교체 가능한 저장소 계층, S3 이벤트 기반 Lambda 후처리

## 빠른 시작 (온프레미스)

WSL2에서 Docker를 쓸 수 있어야 합니다. 준비 과정은 [로컬 호스트 준비](docs/design/DEPLOYMENT.md#1-로컬-호스트-준비)를 따르세요.

```bash
cp .env.example .env        # 비밀번호·JWT secret 교체
docker compose up --build -d
curl http://localhost:8080/api/health
```

검증은 [VALIDATION](docs/design/VALIDATION.md)의 시나리오 순서대로 실행합니다. 백엔드 자동 테스트는 `cd backend && ./gradlew test`(JDK 17)입니다.

## 진행 단계

| 단계 | 내용 |
| --- | --- |
| 0. 준비 | 로컬 WSL2 Docker 환경 |
| 1. 온프레미스 | Compose 구축, 기능·장애 기준선 |
| 2. AWS 마이그레이션 | Terraform 배포, 데이터 이전, 계약 비교 |
| 3. 운영 검증 | 장애 복구, 관측, 배포 통제, 백업 |
| 4. 정리 | 설계 비교, 포트폴리오 |

작업별 수용 기준은 [TASKS](docs/TASKS.md)에 있습니다.

## 문서

- [작업 목록](docs/TASKS.md) · [현재 상태](docs/PROGRESS.md) · [결정 기록](docs/DECISIONS.md)
- [요구사항](docs/PRD.md) · [설계](docs/DESIGN.md) · [포트폴리오](docs/PORTFOLIO.md)
- [배포](docs/design/DEPLOYMENT.md) · [마이그레이션](docs/design/MIGRATION.md) · [검증](docs/design/VALIDATION.md)
- 이전 문서 체계(Day 1~14): [이전 문서 요약](docs/LEGACY.md)
- 문서 위치와 작성 규칙: [AGENTS.md](AGENTS.md#문서-책임)

문서와 코드의 존재는 실제 실행 성공을 뜻하지 않습니다. 수행 근거와 한계는 작업일지에 기록합니다.
