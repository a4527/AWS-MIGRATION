기준일: 2026-11-24 (Asia/Seoul).

## 현재 단계

4단계(비교·정리)다. 온프레미스 기준선(1단계), AWS 배포·계약 비교(2단계), 장애 복구·관측·배포 통제·백업·부하 검증(3단계)을 마쳤다. 2026-11-24에 ARCH-01 설계 비교와 개선 3건(S3 gateway endpoint, deregistration delay 30초, 목록 커서 페이지 PERF-02)의 전후 측정을 마쳤고, 개인 계정 백업(BACKUP-01)은 검증을 마친 뒤 철거했다(DEC-025). PORT-02도 마쳤고(비용은 정가 추정), 남은 것은 선택 과제 K8S-01과 철거 마무리다. 단계별 이전 경과는 [최근 변경](#최근-변경)과 작업일지, 이전 Day 1~14 진행은 [이전 문서 요약](LEGACY.md)에 있다.

## 구현 상태

- 애플리케이션: 인증·사용자·파일 API, `Idempotency-Key` 업로드, 업로드 실패 보상과 관리자 고아 객체 점검, Redis 장애 우회·버전 비교 캐시, MinIO·S3 스트리밍 업로드, 목록 커서 페이지(`size`·`cursor`, PERF-02)가 구현되어 있다. 토큰 갱신·로그아웃, 검사 결과 DB 반영, 감사 로그는 미구현이다([PRD 차이](PRD.md#현재-구현과의-차이)).
- 온프레미스: `docker-compose.yml`(nginx·backend·postgres·redis·minio, 검증용 `mc`)과 `.env.example`이 있다.
- AWS: Terraform state는 S3 원격 state(bootstrap bucket, S3 자체 잠금)다. Terraform이 유지 계층(`dev-data`: VPC·S3·Aurora 0 ACU 자동 일시정지)과 실습 계층(`dev`: NAT·S3 gateway endpoint·ALB·ECS·ElastiCache·Lambda·CloudWatch·선택형 HTTPS)으로 나뉜다([DEC-008](DECISIONS.md#결정-목록)). 기본 리전은 us-east-1이다. 데이터는 옮기지 않는다([DEC-007](DECISIONS.md#결정-목록)).
- 비밀값: DB 비밀번호·JWT secret은 Terraform이 만들어 SSM SecureString에 두고 ECS `secrets`로 주입한다. tfvars·state·plan에 값이 없다([DEC-013](DECISIONS.md#결정-목록)). MIG-02 이후 모든 실습에서 ECS가 이 값으로 기동했다.
- CI/CD(2026-11-20 변경, DEC-015): main push가 테스트·Trivy를 통과하면 ECR push·ECS 배포까지 자동으로 한다. 실습 계층이 없으면 배포만 건너뛴다. 배포 role·ECR은 유지 계층에 있다. 이전 기록: PR 테스트·이미지 빌드·Trivy 검사, main 수동 실행 시 ECR push·ECS 배포 workflow가 있다.

## 검증 상태

| 범위 | 상태 | 근거 |
| --- | --- | --- |
| 백엔드 자동 테스트 | 48개 통과 (H2 PostgreSQL 모드, 모의 Redis·S3, 응답 없는 Redis 포트, 실제 서블릿 multipart 한도) | [작업일지](worklogs/2026-11-17.md) |
| 온프레미스 Compose 실행 | V-ONP·V-FUNC·V-DATA·V-IDEM·V-FAIL·V-ADMIN 통과. 실제 Redis에서 Lua 버전 비교 확인. 다중 backend 경합은 미수행 | [작업일지](worklogs/2026-11-17.md#온프레미스-구축과-검증) |
| AWS 배포 전 로컬 검증 | V-LOC-01·02·03·05·06 통과. V-LOC-04는 실행 중 DB 멈춤(4-1)은 통과, 기동 중 DB 미가용(4-2)은 backend 종료(exit 1) → 배포 전 DB 깨우기로 대응(DEC-010) | [작업일지](worklogs/2026-11-19.md#v-loc-0205-실행) |
| AWS 배포·비교·장애 복구 | MIG-02 배포·smoke, VERIFY-01·02 완료(2026-11-20) | [작업일지](worklogs/2026-11-20.md#1회차-자동-배포-첫-실행과-mig-02verify-01) |
| Terraform | 두 계층 fmt/validate, mock provider plan 테스트, 오프라인 plan Owner 확인. 유지 계층은 실제 apply(27개 생성)·재 plan 변경 없음·예산 생성·Owner 누락 0개. 원격 state(S3 자체 잠금) 이전·잠금 경합·`force-unlock`·버전 복구 확인. 실습 계층 실제 plan/apply는 미수행 | [작업일지](worklogs/2026-11-19.md#유지-계층-apply-port-01), [2026-11-18](worklogs/2026-11-18.md#terraform-계층-분리), IAC-02는 [2026-11-17](worklogs/2026-11-17.md#iac-02-ecs-관리-책임-분리) |
| 비밀값 주입(SEC-01) | 두 계층 validate, mock provider plan 테스트. 유지 계층 apply(SSM 1개 추가, Aurora 비밀번호 교체), plan JSON·state에 새 값 없음, 재 plan 변경 없음. 실습 계층 apply·task 기동 미수행 | [작업일지](worklogs/2026-11-20.md#sec-01-비밀값-주입) |
| AWS 운영 검증 | VERIFY-01(기능·계약 비교), VERIFY-02(task 중지 복구 약 87초, 성능 smoke), OBS-01(요청 ID·SLI), OBS-02(알람 발화·SNS) 확인. 알람 이메일(ALARM·OK) 수신 확인 | [작업일지](worklogs/2026-11-20.md#2회차-verify-02와-obs-0102) |
| 개인 계정 백업(BACKUP-01) | 2026-11-23 검증: S3 교차 계정 복제(27초, 해시 일치, 삭제 미반영), 기존 객체 19개 일회 복사·목록 일치, `pg_dump` task 58초·사본 체크섬 일치·`pg_restore --list`, Lambda·검사 큐 무반응. **2026-11-24 철거**(DEC-025): 복제 규칙·role 삭제 완료, 개인 계정 bucket·IAM은 사용자 콘솔 삭제 대기 | [작업일지](worklogs/2026-11-23.md#backup-01-개인-계정-백업) |
| 부하·확장(PERF-01) | 40 VU 같은 조건: 기준선(task 1) 59.4 rps·오류 0, Auto Scaling 1~4 72.9 rps·오류 0. 확장 후 축소 안 됨 → 메모리 정책 축소 끔(DEC-023) → 3 → 1 확인. Auto Scaling 3 task 상태에서 plan 변경 없음(IAC-02). task 중지 재실행: 중단 약 6분 44초, `no-healthy-target` 발화 확인 | [작업일지](worklogs/2026-11-24.md) |
| 설계 비교·개선(ARCH-01, PERF-02) | 같은 40 VU 조건 처리량 59.4 → 206.9 rps, 조회 p95 1,783 → 121ms(페이지 처리). task 중지 중단 404 → 92초(delay 30초, rolling 배포 시간은 줄지 않음). S3 210MB 중 NAT 0.6MB, 업로드 바이트당 NAT 송신 2.25 → 1.12배(endpoint). k6 시험 트래픽의 NAT 왕복 발견 | [작업일지](worklogs/2026-11-24.md#arch-01perf-02-변경-후-측정), [설계 비교](proposals/ARCH-COMPARISON.md) |
| GitHub Actions | actionlint, Trivy 0건, 테스트 69개 통과. GitHub 실제 실행: PR 검사, main 자동 배포(staging·smoke·승인 후 production), 롤백 drill(11-23), Terraform PR plan·승인 apply·drift 탐지 | [2026-11-16](worklogs/2026-11-16.md#cicd-01-pr이미지-검증-구현), [2026-11-20](worklogs/2026-11-20.md#cicd-자동-배포와-trivy-수정) |

## 다음 작업

1. 개인 계정 bucket·IAM role·user 콘솔 삭제(사용자) → `terraform/environments/backup` 제거.
2. 조직 관리자에게 Owner 비용 할당 태그 활성 여부 확인.
3. 프로젝트 종료 시 Terraform 밖 리소스(Container Insights 로그 그룹 등) 정리.

작업별 의존성과 수용 기준은 [TASKS](TASKS.md)에서 관리한다.

## 최근 변경

- 2026-11-24 오후: ARCH-01 완료(설계 비교, 개선 3건 전후 측정), PERF-02 목록 커서 페이지 배포(API 호환성 깨짐, DEC-024), S3 gateway endpoint·deregistration delay 30초(DEC-026), 개인 계정 백업 철거(DEC-025). PR #13·#14 머지. PORT-02 완료(캡처 C27~C30).
- 2026-11-24: 실습. PERF-01 기준선·확장 비교 완료, 메모리 정책이 축소를 막는 결함을 찾아 고쳤다(DEC-023). IAC-02 완료. 포트폴리오 캡처 구조(C01~C26)를 만들고 28장을 모두 반영했다. task 중지 재실행에서 중단이 6분 44초로 길어진 것을 기록했다(원인 미확인). 시험 데이터 7,183건 정리 후 실습 계층 destroy.
- 2026-11-23: 실습. SEC-02(Boot 4 이미지 production 배포), IAC-03(drift 주입 → 탐지 47초 → 승인 apply 복구), CICD-03 drill B(production 런타임 실패 → smoke 감지 → 자동 롤백 3분 13초)를 마쳤다.
- 2026-11-23: BACKUP-01. 부트캠프 file bucket을 개인 계정 bucket으로 복제하고(삭제 미복제), 실습 종료 전 `scripts/db-backup.sh`로 DB dump를 같은 경로로 보낸다(DEC-022). 부하 시험 데이터(파일 1,675개, 260MB)를 정리했다. Aurora가 11-20 밤 외부 요인으로 정지돼 있던 것을 발견해 다시 시작했다.
- 2026-11-23: SEC-03. Aurora SG·route table을 Terraform CI 밖에서 바꾸면 CloudTrail → EventBridge → SNS로 1분 안에 알린다(DEC-021). 유지 계층에 trail을 추가했다.
- 2026-11-23: SEC-02. Spring Boot 4.1.3·Jackson 3로 올려 Trivy 예외 2건을 지웠다. 로컬 테스트·Trivy·V-FUNC·V-LOC-02/03 통과, AWS 배포는 다음 실습에서 확인한다.
- 2026-11-23: PR #7 머지. 정기 drift 검사가 실습 계층과 공유하는 SG·route table 때문에 매일 오탐을 내서 `-refresh-only` 대신 일반 plan으로 바꿨다.
- 2026-11-20: 3단계 실행. OPS-01·OPS-02·FILE-03·CICD-02 완료, CICD-03·IAC-03 일부, PERF-01 중단. 장애 훈련과 첫 승격 배포에서 결함 6개를 찾아 고쳤다(저장소 장애 404, 알람 누락 2건, grace period, ALB 조회 권한, 배포 실패 알림 부재). DEC-017~020.
- 2026-11-20: 1·2회차 실행. 실습 계층 배포(MIG-02), 기능·계약 비교(VERIFY-01), 장애 주입·성능 smoke(VERIFY-02), 요청 ID·구조화 로그·대시보드·SLI(OBS-01), 알람 SNS 알림(OBS-02).
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
