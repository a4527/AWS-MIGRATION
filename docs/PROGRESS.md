기준일: 2026-11-17 (Asia/Seoul).

## 현재 단계

1단계(온프레미스)를 마치고 2단계(AWS 마이그레이션) 준비 단계다. 로컬 WSL2 + Docker Desktop에서 Compose 스택을 띄워 기능·장애·정합성 시나리오를 통과했다. 다음은 PORT-01과 MIG-01이다. 이전 Day 1~14 진행은 [이전 문서 요약](LEGACY.md)에 남겼다.

## 구현 상태

- 애플리케이션: 인증·사용자·파일 API, `Idempotency-Key` 업로드, 업로드 실패 보상과 관리자 고아 객체 점검, Redis 장애 우회·버전 비교 캐시, MinIO·S3 스트리밍 업로드가 구현되어 있다. 토큰 갱신·로그아웃, 검사 결과 DB 반영, 감사 로그는 미구현이다([PRD 차이](PRD.md#현재-구현과의-차이)).
- 온프레미스: `docker-compose.yml`(nginx·backend·postgres·redis·minio, 검증용 `mc`)과 `.env.example`이 있다.
- AWS: Terraform에 VPC·ECS·Aurora·ElastiCache·S3·Lambda·CloudWatch·선택형 HTTPS·OIDC 배포 role이 있다. 데이터 이전 경로는 미정(MIG-01)이다.
- CI/CD: PR 테스트·이미지 빌드·Trivy 검사, main 수동 실행 시 ECR push·ECS 배포 workflow가 있다.

## 검증 상태

| 범위 | 상태 | 근거 |
| --- | --- | --- |
| 백엔드 자동 테스트 | 48개 통과 (H2 PostgreSQL 모드, 모의 Redis·S3, 응답 없는 Redis 포트, 실제 서블릿 multipart 한도) | [작업일지](worklogs/2026-11-17.md) |
| 온프레미스 Compose 실행 | V-ONP·V-FUNC·V-DATA·V-IDEM·V-FAIL·V-ADMIN 통과. 실제 Redis에서 Lua 버전 비교 확인. 다중 backend 경합은 미수행 | [작업일지](worklogs/2026-11-17.md#온프레미스-구축과-검증) |
| AWS 배포·비교·장애 복구 | 미수행 | PORT-01 이후 |
| Terraform | fmt/validate, ECS lifecycle 합성 state plan까지 (IAC-02) | [작업일지](worklogs/2026-11-17.md#iac-02-ecs-관리-책임-분리) |
| GitHub Actions | 정적 검증까지 (CICD-01) | [작업일지](worklogs/2026-11-16.md#cicd-01-pr이미지-검증-구현) |

## 다음 작업

1. PORT-01: AWS 계정·리전·인증 방식·예산 한도·정리 범위 확정.
2. MIG-01: [데이터 이전 방식](proposals/DATA-MIGRATION.md) 결정. 현재 데이터는 검증으로 만든 사용자 3명·파일 8건(약 12MB)이다.
3. 권장 선행: SEC-01(비밀값 주입), IAC-01(원격 state).
4. MIG-02 → VERIFY-01: AWS 배포·데이터 이전·계약 비교.

작업별 의존성과 수용 기준은 [TASKS](TASKS.md)에서 관리한다.

## 최근 변경

- 2026-11-17: ENV-01, ONP-01~03 실행. 공식 MinIO 이미지 배포 중단으로 같은 버전의 Bitnami 보관 이미지로 바꿨다(DEC-005).
- 2026-11-17: 문서를 단계별로 재구성하고 이전 체계를 `legacy-docs-2026-11-17` 태그와 [요약](LEGACY.md)으로 보존했다. 검증 시나리오를 시나리오 ID 기반으로 다시 썼다. 작성 중 발견한 두 결함을 고쳤다: Spring multipart 한도가 기본 1MB여서 1~10MB 업로드가 500으로 실패하던 문제, Redis 명령 타임아웃이 기본 60초라 Redis 중지 시 요청이 오래 멈추던 문제.
- 2026-11-17: FILE-01/02/04/05, LAYER-01/02 구현과 자동 테스트. IAC-02 로컬 plan 검증.
- 2026-11-16: 포트폴리오 보강 작업 등록, CICD-01 구현.
