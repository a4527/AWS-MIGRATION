기준일: 2026-11-20 (Asia/Seoul).

## 현재 단계

1단계(온프레미스)를 마치고 2단계(AWS 마이그레이션) 준비 단계다. 로컬 WSL2 + Docker Desktop에서 Compose 스택을 띄워 기능·장애·정합성 시나리오를 통과했다. 2026-11-18에 MIG-01(데이터 이전 없음)을 결정하고, PORT-01의 리전(us-east-1)·인증(access key)·보존 범위를 정해 Terraform을 유지/실습 계층으로 나눴다. 예산(월 $10)과 Owner 태그 강제를 코드에 넣었고 aws CLI·Terraform을 설치했다. access key로 로그인해 유지 계층 plan까지 확인했다. 2026-11-19에 AWS 배포 전 로컬 검증(V-LOC)을 마치고 유지 계층(`dev-data`)을 실제로 만든 뒤 state를 S3 원격 state로 옮겼다(IAC-01). 실습 계층(`dev`)은 아직 만들지 않았다. 2026-11-20에 SEC-01(SSM SecureString + write-only 생성)을 유지 계층에 적용했다. 장기 access key가 삭제되어 부트캠프 계정 인증을 `aws login`으로 바꿨다(DEC-014). 이전 Day 1~14 진행은 [이전 문서 요약](LEGACY.md)에 남겼다.

## 구현 상태

- 애플리케이션: 인증·사용자·파일 API, `Idempotency-Key` 업로드, 업로드 실패 보상과 관리자 고아 객체 점검, Redis 장애 우회·버전 비교 캐시, MinIO·S3 스트리밍 업로드가 구현되어 있다. 토큰 갱신·로그아웃, 검사 결과 DB 반영, 감사 로그는 미구현이다([PRD 차이](PRD.md#현재-구현과의-차이)).
- 온프레미스: `docker-compose.yml`(nginx·backend·postgres·redis·minio, 검증용 `mc`)과 `.env.example`이 있다.
- AWS: Terraform state는 S3 원격 state(bootstrap bucket, S3 자체 잠금)다. Terraform이 유지 계층(`dev-data`: VPC·S3·Aurora 0 ACU 자동 일시정지)과 실습 계층(`dev`: NAT·ALB·ECS·ECR·ElastiCache·Lambda·CloudWatch·선택형 HTTPS·OIDC 배포 role)으로 나뉜다([DEC-008](DECISIONS.md#결정-목록)). 기본 리전은 us-east-1이다. 데이터는 옮기지 않는다([DEC-007](DECISIONS.md#결정-목록)).
- 비밀값: DB 비밀번호·JWT secret은 Terraform이 만들어 SSM SecureString에 두고 ECS `secrets`로 주입한다. tfvars·state·plan에 값이 없다([DEC-013](DECISIONS.md#결정-목록)). 유지 계층에는 적용했고(Aurora 비밀번호 교체), ECS가 이 값으로 기동하는지는 MIG-02에서 확인한다.
- CI/CD(2026-11-20 변경, DEC-015): main push가 테스트·Trivy를 통과하면 ECR push·ECS 배포까지 자동으로 한다. 실습 계층이 없으면 배포만 건너뛴다. 배포 role·ECR은 유지 계층에 있다. 이전 기록: PR 테스트·이미지 빌드·Trivy 검사, main 수동 실행 시 ECR push·ECS 배포 workflow가 있다.

## 검증 상태

| 범위 | 상태 | 근거 |
| --- | --- | --- |
| 백엔드 자동 테스트 | 48개 통과 (H2 PostgreSQL 모드, 모의 Redis·S3, 응답 없는 Redis 포트, 실제 서블릿 multipart 한도) | [작업일지](worklogs/2026-11-17.md) |
| 온프레미스 Compose 실행 | V-ONP·V-FUNC·V-DATA·V-IDEM·V-FAIL·V-ADMIN 통과. 실제 Redis에서 Lua 버전 비교 확인. 다중 backend 경합은 미수행 | [작업일지](worklogs/2026-11-17.md#온프레미스-구축과-검증) |
| AWS 배포 전 로컬 검증 | V-LOC-01·02·03·05·06 통과. V-LOC-04는 실행 중 DB 멈춤(4-1)은 통과, 기동 중 DB 미가용(4-2)은 backend 종료(exit 1) → 배포 전 DB 깨우기로 대응(DEC-010) | [작업일지](worklogs/2026-11-19.md#v-loc-0205-실행) |
| AWS 배포·비교·장애 복구 | 미수행 | PORT-01 이후 |
| Terraform | 두 계층 fmt/validate, mock provider plan 테스트, 오프라인 plan Owner 확인. 유지 계층은 실제 apply(27개 생성)·재 plan 변경 없음·예산 생성·Owner 누락 0개. 원격 state(S3 자체 잠금) 이전·잠금 경합·`force-unlock`·버전 복구 확인. 실습 계층 실제 plan/apply는 미수행 | [작업일지](worklogs/2026-11-19.md#유지-계층-apply-port-01), [2026-11-18](worklogs/2026-11-18.md#terraform-계층-분리), IAC-02는 [2026-11-17](worklogs/2026-11-17.md#iac-02-ecs-관리-책임-분리) |
| 비밀값 주입(SEC-01) | 두 계층 validate, mock provider plan 테스트. 유지 계층 apply(SSM 1개 추가, Aurora 비밀번호 교체), plan JSON·state에 새 값 없음, 재 plan 변경 없음. 실습 계층 apply·task 기동 미수행 | [작업일지](worklogs/2026-11-20.md#sec-01-비밀값-주입) |
| GitHub Actions | actionlint, 로컬 Trivy(예외 2건 적용 후 0건), 테스트 48개 통과. GitHub 실제 실행(PR·자동 배포·건너뜀 경로) 미수행 | [2026-11-16](worklogs/2026-11-16.md#cicd-01-pr이미지-검증-구현), [2026-11-20](worklogs/2026-11-20.md#cicd-자동-배포와-trivy-수정) |

## 다음 작업

1. 조직 관리자에게 Owner 비용 할당 태그 활성 여부 확인(비활성이면 `budget_filter_by_owner_tag = false` 검토).
2. `~/.aws/credentials`의 삭제된 키 정리(사용자). 프로필 없이 실행한 명령이 이 키로 실패한다.
3. MIG-02 → VERIFY-01: 실습 계층 배포(첫 배포 전 [DB 깨우기](design/DEPLOYMENT.md#46-github-actions-배포-실행)), 빈 상태 smoke, Aurora 일시정지·재개 확인, 계약 비교.
4. BACKUP-01: [개인 계정 백업](adr/002-CROSS-ACCOUNT-BACKUP.md)(S3 교차 계정 복제 + `pg_dump`, DEC-012). 개인 계정 CLI 접근은 준비했다. S3 복제는 먼저 해도 된다.

작업별 의존성과 수용 기준은 [TASKS](TASKS.md)에서 관리한다.

## 최근 변경

- 2026-11-20: CD를 main push 자동 배포로 바꾸고 배포 role·ECR을 유지 계층으로 옮겼다(DEC-015). Trivy 실패 원인이던 의존성을 Spring Boot 3.5.16 등으로 올렸고, 남은 spring-webmvc 2건은 만료일 있는 예외로 두었다(SEC-02).
- 2026-11-20: 부트캠프 계정 인증을 `aws login` + `credential_process`(`bootcamp-tf`)로 바꿨다(DEC-014). 장기 키가 삭제된 것을 확인했다.
- 2026-11-20: SEC-01 구현·유지 계층 적용. DB 비밀번호(유지 계층)·JWT secret(실습 계층)을 Terraform ephemeral 값으로 만들어 write-only로 Aurora·SSM에 쓰고, ECS는 `secrets`로 읽는다. 두 tfvars의 비밀값 변수를 없앴다(DEC-013). 유지 계층 적용 완료, task 기동은 MIG-02.
- 2026-11-19: 공유 계정 정리 정책 대비 개인 계정 백업(S3 교차 계정 복제 + `pg_dump`)을 채택했다(DEC-012, ADR-002). 미구현.
- 2026-11-19: IAC-01. `terraform/bootstrap`으로 state bucket을 만들고 두 계층 backend를 S3(계층별 key, `use_lockfile`)로 바꿨다(DEC-011). dev-data state를 옮기고 잠금·복구를 시험했다.
- 2026-11-19: 유지 계층(`dev-data`) apply. VPC·S3·Aurora(0~2 ACU, 300초 자동 일시정지, 삭제 보호)·월 $10 예산 생성, 재 plan 변경 없음. 거부된 access key를 새 키로 교체했다.
- 2026-11-19: V-LOC-02~05 실행. `s3` 프로필·컨테이너 한도·Lambda 후처리는 통과했다. Aurora 일시정지 중 backend가 기동하면 DB 연결 실패로 종료되는 것을 확인했고, 코드는 그대로 두고 배포 전 DB를 깨우기로 했다(DEC-010). V-LOC-04 4-2 시나리오의 시작 명령 오류와 재현 불가(20초 pause)를 고쳤다.
- 2026-11-18: MIG-01을 데이터 이전 없음으로 결정(DEC-007). PORT-01에서 us-east-1·access key 인증을 정하고(DEC-006), 비용 비교 후 Terraform을 유지/실습 계층으로 나눴다. Aurora는 최소 0 ACU 자동 일시정지·삭제 보호를 켰다(DEC-008). 모든 리소스에 Owner 태그를 강제하고 월 $10 예산을 유지 계층에 추가했다(DEC-009).
- 2026-11-17: ENV-01, ONP-01~03 실행. 공식 MinIO 이미지 배포 중단으로 같은 버전의 Bitnami 보관 이미지로 바꿨다(DEC-005).
- 2026-11-17: 문서를 단계별로 재구성하고 이전 체계를 `legacy-docs-2026-11-17` 태그와 [요약](LEGACY.md)으로 보존했다. 검증 시나리오를 시나리오 ID 기반으로 다시 썼다. 작성 중 발견한 두 결함을 고쳤다: Spring multipart 한도가 기본 1MB여서 1~10MB 업로드가 500으로 실패하던 문제, Redis 명령 타임아웃이 기본 60초라 Redis 중지 시 요청이 오래 멈추던 문제.
- 2026-11-17: FILE-01/02/04/05, LAYER-01/02 구현과 자동 테스트. IAC-02 로컬 plan 검증.
- 2026-11-16: 포트폴리오 보강 작업 등록, CICD-01 구현.
