작업 상태의 기준 문서다. 2026-11-17에 단계별로 재구성했다. 이전 Day 1~14 체크리스트와 문서 체계는 [이전 문서 요약](LEGACY.md)과 [Day 이력](worklogs/LEGACY.md)에 보존하며, 기존 작업 ID와 수용 기준은 아래 단계로 옮겨 유지했다. 현재 상태 요약은 [PROGRESS](PROGRESS.md), 실행 근거는 수행일의 작업일지를 따른다.

## 진행 단계

| 단계 | 목표 | 핵심 작업 |
| --- | --- | --- |
| 0. 준비 | 로컬 WSL2에서 컨테이너를 실행할 수 있다 | ENV-01 |
| 1. 온프레미스 구축·컨테이너 실행 | 현재 코드로 Compose 스택을 띄우고 기능·장애 기준선을 남긴다 | ONP-01~03, FILE-01/02/04/05 실서버 검증 |
| 2. AWS 마이그레이션 | 현재 Terraform 구성으로 배포하고 데이터를 옮겨 계약을 비교한다 | PORT-01, MIG-01/02, IAC-01/02, SEC-01, CICD-01, VERIFY-01 |
| 3. 운영 검증·보강 | 장애 복구·관측·배포 통제·백업을 검증한다 | VERIFY-02, IAC-03, CICD-02/03, OBS-01/02, OPS-01/02, FILE-03, PERF-01 |
| 4. 비교·정리 | 측정 근거로 설계를 비교하고 외부 설명 자료를 완성한다 | ARCH-01, PORT-02, K8S-01(선택) |

우선순위: P0은 단계 진입 조건, P1은 핵심, P2는 비교·정리, P3은 선택이다. 같은 우선순위에서는 선행 조건을 따른다. 상태 표기: 예정 / 구현·로컬 검증(자동 테스트까지) / 실행 결과 미확인 / 완료(수용 기준 전체 충족). 작업 등록·문서 작성은 실행 성공을 뜻하지 않는다.

## 0단계 - 준비

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| ENV-01 | P0 | 완료 | 로컬 호스트 준비. 선행 없음 | WSL2 셸에서 `docker version`·`docker compose version`이 성공하고 `jq`·`curl`이 있다. 저장소가 WSL 파일시스템(`/home/...`)에 있고 WSL 메모리가 4GB 이상인지 확인한다. [배포 기준](design/DEPLOYMENT.md#1-로컬-호스트-준비) |

## 1단계 - 온프레미스 구축·컨테이너 실행

온프레미스는 현재 로컬 PC의 WSL2에서 Docker Compose로 실행한다. 2026-11-17에 ENV-01, ONP-01~03을 실행해 FILE-01/02/05를 실제 PostgreSQL·Redis·MinIO에서 확인했다([결과](worklogs/2026-11-17.md#온프레미스-구축과-검증)). 단일 backend로 실행해 FILE-02의 다중 인스턴스 무효화 실패는 재현하지 않았다. AWS 쪽 검증은 2·3단계에서 이어진다. 결정 근거는 [결정 목록](DECISIONS.md#결정-목록)의 DEC-001~003을 따른다.

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| ONP-01 | P0 | 완료 | 온프레미스 스택 구축. ENV-01 선행 | `.env`를 만들고 DB·MinIO 비밀번호와 JWT secret 기본값을 교체한다. `docker compose up --build -d` 후 [V-ONP-01](design/VALIDATION.md#v-onp-01-스택-기동)을 통과하고 `users`·`files`·`upload_requests` 테이블과 볼륨 생성을 확인한다. [구축 절차](design/DEPLOYMENT.md#3-온프레미스-구축) |
| ONP-02 | P0 | 완료 | 온프레미스 기능 기준선. ONP-01 선행 | [기능 시나리오](design/VALIDATION.md#2-기능-시나리오) V-FUNC 전체와 [저장소 계약](design/VALIDATION.md#3-저장소-계약) V-DATA를 실행하고 응답·저장소 상태를 작업일지에 기록한다. 이 결과를 VERIFY-01의 온프레미스 기준선으로 쓴다. |
| ONP-03 | P1 | 완료 (다중 인스턴스 경합 제외) | 온프레미스 정합성·장애 검증. ONP-02 선행 | [멱등성](design/VALIDATION.md#4-멱등성) V-IDEM, [장애 주입](design/VALIDATION.md#5-장애-주입) V-FAIL, [관리자 점검](design/VALIDATION.md#6-관리자-정합성-점검) V-ADMIN을 실행한다. 실제 Redis에서 Lua 버전 비교, 실제 PostgreSQL에서 유니크 제약·조건부 갱신, MinIO에서 크기 지정 업로드와 해시 일치를 확인하고, 결과로 FILE-01/02/05의 실서버 검증과 FILE-04의 MinIO 부분을 판정한다. |

### 파일 서비스 정합성과 장애 처리

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| FILE-01 | P1 | 온프레미스 검증 완료, AWS 확인 대기 | 업로드의 객체·DB 정합성과 실패 복구. 로컬 설계·구현 선행 없음, AWS 실행은 PORT-01 선행 | 객체 저장 성공 후 DB 저장 실패 시 보상·정리 방식을 정의하고 구현. 보상 실패·요청 재시도·응답 유실 시 중복과 미참조 객체를 식별·복구할 수 있는지 검증. DB 트랜잭션만으로 객체 저장까지 원자화된다고 가정하지 않으며, 정상 참조 객체와 논리 삭제로 보존한 객체가 정리 대상에 포함되지 않음을 확인. 저장소·DB 실패 주입으로 API 응답·DB/object 잔여 상태·재처리 결과를 기록. [구조](DESIGN.md)·[데이터 계약](design/DATABASE.md)·[API 오류](design/API.md)·[검증](design/VALIDATION.md)에 반영 |
| FILE-02 | P1 | 온프레미스 검증, 다중 인스턴스 재현·AWS 확인 대기 | 캐시 장애 격리와 삭제·조회 일관성. 로컬 설계·구현 선행 없음, AWS 실행은 PORT-01 선행 | Redis 읽기·쓰기·삭제 실패별 처리와 DB를 기준으로 한 조회 경로를 정의. 캐시 장애만으로 정상 DB 조회가 실패하거나 DB 반영과 API 결과가 모순되지 않는지 검증. 동시 조회의 오래된 값 재삽입·삭제 직후 재조회·다중 인스턴스에서의 무효화 실패를 재현하고, 삭제 완료 후 새 조회·다운로드가 허용되지 않도록 검증. 권한 확인은 캐시 유무와 관계없이 유지. 선택한 무효화·복구 방식과 한계를 [구조](DESIGN.md)·[API](design/API.md)·[검증](design/VALIDATION.md)·[트러블슈팅](TROUBLESHOOTING.md)에 반영. 운영 훈련은 OPS-01에서 이 결과를 재사용 |
| FILE-04 | P2 | MinIO 업로드 확인, S3 메모리 측정 대기 | S3 업로드 스트리밍과 자원 사용 검증. FILE-01 선행, AWS 실행은 PORT-01 선행 | 파일 크기·스트림 수명을 저장소 인터페이스에서 어떻게 전달할지 정하고 S3의 전체 파일 `readAllBytes()` 버퍼링을 제거. 현재 파일 크기 제한을 유지하며 local/MinIO/S3의 크기·해시 일치와 스트림 정리, 전송 중단·저장 실패 처리를 검증. 같은 파일 크기·동시 업로드 수에서 변경 전후 메모리 사용과 오류율을 측정하고 제한·한계를 기록. [구조](DESIGN.md)·[검증](design/VALIDATION.md)에 반영. 전체 부하·스케일링은 PERF-01, presigned URL 방식 비교는 ARCH-01에서 추적 |
| FILE-05 | P1 | 온프레미스 검증 완료, AWS 확인 대기 | 업로드 멱등성 키. FILE-01과 함께 구현, 결정 [ADR-001](adr/001-UPLOAD-IDEMPOTENCY-KEY.md) | `Idempotency-Key`로 같은 요청의 재전송을 한 번만 처리하고 첫 결과를 돌려준다. 동시 요청·다른 요청·실패 후 재시도·만료·중단된 요청 이어받기·늦게 끝난 이전 시도의 롤백을 검증. 키 기록은 DB `upload_requests`에 두며 [API](design/API.md#post-apifiles)·[DATABASE](design/DATABASE.md#업로드-요청-테이블)에 계약을 반영. 실제 PostgreSQL에서 유니크 제약·조건부 갱신과 동시 요청을 확인해야 완료 |

## 2단계 - AWS 마이그레이션

대상 구성은 현재 Terraform(ECS Fargate, Aurora PostgreSQL, ElastiCache Redis, S3, Lambda, CloudWatch)이다. AWS 실행은 PORT-01의 계정·예산·정리 조건이 확정된 뒤에만 수행한다.

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| PORT-01 | P0 | 예정 (조건 확정·도구 설치 완료, 유지 계층 apply 남음) | AWS 검증 환경·예산·실험 범위 준비. 선행 없음 | 계정·리전·인증 방식, 실습 데이터, 비용 한도·알림, 리소스 보존/삭제 범위, 실험 중단·복구 조건을 비밀값 없이 기록하고 [AWS 런북](design/DEPLOYMENT.md#40-사전-준비)에 반영. VERIFY-01/02 실행 전에 확인. 2026-11-18: us-east-1·access key([DEC-006](DECISIONS.md#결정-목록)), 유지/실습 계층 분리([DEC-008](DECISIONS.md#결정-목록)) 확정. 예산 월 $10·이메일 알림, Owner 태그 강제([DEC-009](DECISIONS.md#결정-목록)), aws CLI 2.41.0·Terraform 1.17.2 설치. access key 로그인과 `aws sts get-caller-identity` 확인, 유지 계층 실제 plan(27개 생성) 완료. 남은 것: [V-LOC](design/VALIDATION.md#7a-aws-배포-전-로컬-검증) 로컬 사전 검증(01·06 통과, 02~05는 Docker 필요), 유지 계층 apply, Owner 비용 할당 태그 활성 여부(연결 계정이라 직접 조회 불가) 확인 |
| MIG-01 | P1 | 완료 (이전 없음 결정) | 데이터 이전 방식 결정. ONP-02 선행 | [데이터 이전 제안](proposals/DATA-MIGRATION.md)의 대안 중 하나를 합의해 [결정 목록](DECISIONS.md#결정-목록)에 기록한다. Aurora 접근 경로, `storage_path` 접두어(`files/`) 변환, 쓰기 중지 시간, 롤백 기준과 필요한 Terraform 변경을 확정한다. 2026-11-18 대안 A(이전 없음)로 결정([DEC-007](DECISIONS.md#결정-목록)). 접근 경로·접두어 변환·쓰기 중지가 필요 없고 Terraform 변경도 없다. 롤백은 실습 계층 destroy다 |
| MIG-02 | P1 | 예정 | AWS 배포. PORT-01·MIG-01·ONP-02 선행, SEC-01 권장 | [AWS 배포](design/DEPLOYMENT.md#4-aws-배포)로 유지 계층(`dev-data`)·실습 계층(`dev`)과 애플리케이션을 배포하고 [전환 절차](design/MIGRATION.md#전환-절차)의 1~4단계를 수행한다. 빈 DB·bucket에서 V-AWS-01 smoke를 통과하고, Aurora 0 ACU 자동 일시정지·재개(재개 소요 시간 포함)와 실습 계층 destroy 후 유지 계층 plan이 변경 없음(no changes)인지 확인한다. MIG-01에서 데이터 이전을 하지 않기로 해서([DEC-007](DECISIONS.md#결정-목록)) V-MIG는 범위에서 제외한다 |
| IAC-01 | P1 | 예정 | 원격 state와 환경 분리 보강. PORT-01 선행. 유지/실습 계층 state 분리는 2026-11-18에 구현([DEC-008](DECISIONS.md#결정-목록)) | 두 계층(`dev-data`, `dev`) 각각의 state bootstrap·접근 권한·암호화·버전 관리·잠금·복구 절차를 구현하고 동시 실행 잠금과 복구를 검증. Terraform 버전과 S3/DynamoDB 잠금 방식의 호환성을 확인하고 환경별 state 격리를 검증. [배포 기준](design/DEPLOYMENT.md)·[Terraform 안내](../terraform/README.md)에 반영 |
| IAC-02 | P1 | 구현·로컬 plan 검증, AWS 실행 검증 대기 | Terraform과 CD·Auto Scaling의 관리 책임 정리. 선행 없음 | task definition·service 배포 버전·desired count의 관리 주체와 최초 배포 순서를 정의. CD 배포·스케일 변경 후 plan/apply가 이를 의도치 않게 되돌리지 않는지 검증하고 [아키텍처](DESIGN.md)·[배포 기준](design/DEPLOYMENT.md#ecs-관리-책임과-배포-순서-iac-02)에 반영. [로컬 근거](worklogs/2026-11-17.md)의 범위를 넘어 실제 AWS plan/apply 확인이 남음 |
| SEC-01 | P1 | 예정 | 비밀값 주입과 배포 권한 분리. PORT-01 선행 | DB 비밀번호·JWT secret을 Secrets Manager 참조로 주입하고 task definition 일반 환경변수에서 제거. execution/task/CI role의 필요한 권한과 OIDC 신뢰 조건을 검증. Terraform sensitive 표기만으로 state의 비밀값이 제거되지 않음을 고려해 생성·저장 경로를 점검하고 로그·plan 노출 여부를 확인. [배포 기준](design/DEPLOYMENT.md)에 반영 |
| CICD-01 | P1 | 구현·정적 검증, 실행 검증 대기 | PR CI와 이미지 검증. 선행 없음 | PR에서 백엔드 테스트·이미지 빌드·취약점 검사 수행. 차단 기준과 예외 처리 방식을 정의하고 실패 시 배포 차단을 확인. [구현·검증 근거](worklogs/2026-11-16.md#cicd-01-pr이미지-검증-구현)의 미확인 항목과 GitHub 실행 증빙을 보완해야 완료. AWS 배포 권한은 배포 job에 한정하고 [배포 기준](design/DEPLOYMENT.md)에 반영 |
| VERIFY-01 | P1 | 실행 결과 미확인 | 온프레미스/AWS 계약 비교. ONP-02·MIG-02 선행 | ONP-02 기준선과 같은 V-FUNC·V-DATA·V-IDEM을 [AWS 검증](design/VALIDATION.md#8-aws-검증)에서 실행하고 응답·파일 해시·object key·cache key 결과를 비교해 작업일지에 연결한다. |

## 3단계 - 운영 검증·보강

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| VERIFY-02 | P1 | 실행 결과 미확인 | AWS 장애 주입·복구·smoke 성능. VERIFY-01 선행, 관측·복구 수단 준비 필요 | [AWS 검증](design/VALIDATION.md#8-aws-검증)의 V-AWS-04~06(ECS task 중지, Lambda 실패, 알람)과 [성능 smoke](design/VALIDATION.md#9-성능-smoke) 결과와 한계·복구·정리 여부를 작업일지에 연결한다. |
| IAC-03 | P1 | 예정 | Terraform PR 검증·승인 적용·drift 탐지. IAC-01/02 선행 | fmt/validate·보안 검사·plan 검토·승인 후 검토한 plan 적용 흐름 구현. 읽기/적용 권한과 신뢰된 PR 경계를 분리하고 plan/state 비밀값 노출 방지. 정기 plan으로 의도적 drift 탐지 후 검토·복구를 검증하고 [AWS 런북](design/DEPLOYMENT.md)에 반영 |
| CICD-02 | P1 | 예정 | 이미지 승격·환경 승인·배포 후 smoke test. VERIFY-01, IAC-02, SEC-01, CICD-01 선행 | 검증한 동일 image digest로 테스트 환경 검증과 배포 대상 환경 승격을 수행. 환경별 승인·동시 실행 제어·OIDC 조건을 적용하고 로그인/업로드/다운로드 smoke 실패를 배포 실패로 처리. 커밋·digest·task revision을 추적 가능하게 기록 |
| OBS-01 | P1 | 예정 | 서비스 지표·구조화 로그·대시보드. VERIFY-01 선행 | API별 성공률·p95·요청량과 ALB target health, ECS, DB, Lambda 지표를 연결. 요청 ID로 API 로그 추적을 시연하고 비밀값·파일 내용은 제외. 조회/전송의 SLI·SLO 대상·기간·임계값을 측정 근거로 정의하며 짧은 실험을 장기 가용성 실적으로 표시하지 않음. [최종 검증](design/VALIDATION.md)에 반영 |
| OBS-02 | P1 | 예정 | 알람 전달과 대응 런북. OBS-01 선행 | CloudWatch alarm action과 알림 수신 경로를 구성하고 의도적 오류의 알람 발화·수신·정상 복귀를 확인. 알람별 증상·진단·복구·에스컬레이션 기준을 [AWS 런북](design/DEPLOYMENT.md)·[트러블슈팅](TROUBLESHOOTING.md)에 연결 |
| CICD-03 | P1 | 예정 | 실패 배포 감지·롤백. CICD-02, OBS-02 선행 | ECS 기동/health 실패와 기동 후 오류율 증가에 대한 감지·롤백 방식 구성. 의도적 실패 배포에서 이전 정상 revision 복구와 API 정상화를 검증하고 감지·복구 시간 및 자동/수동 복구 한계를 기록 |
| OPS-01 | P1 | 예정 | 장애 대응 훈련·사후 분석. VERIFY-02, OBS-02, CICD-03 선행 | 기존 VERIFY-02 결과를 재사용하고 ECS task 종료·실패 배포에 대한 타임라인, 사용자 영향, 감지/복구 시간, 원인·개선점을 기록. 캐시 장애 시 실제 API 동작과 복구 한계를 확인하고 FILE-02 구현 후에는 그 검증 결과를 재사용. 실험 중단·복구·잔여 리소스를 확인하고 [최종 검증](design/VALIDATION.md)·당일 작업일지에 연결 |
| OPS-02 | P1 | 예정 | DB·object storage 백업 복원 검증. VERIFY-01, SEC-01 선행 | 백업 보존·삭제 보호 정책과 RTO/RPO 목표를 정의. 별도 환경에 DB 백업 복원 후 storage_path와 S3 object/version 정합성 및 실제 다운로드 해시를 확인. 복원 소요·데이터 손실 범위·목표 충족 여부·정리 결과를 기록하고 [AWS 런북](design/DEPLOYMENT.md)·[최종 검증](design/VALIDATION.md)에 반영 |
| FILE-03 | P1 | 예정 | 검사 상태와 후처리 결과의 DB·캐시 연결. FILE-01/02 선행, AWS 실행은 PORT-01·SEC-01 선행 | 업로드 직후 `AVAILABLE`·`CLEAN`을 기록하는 현재 동작과 [요구사항의 상태 전이](PRD.md#파일-라이프사이클) 차이를 정리하고 검사 완료 전 다운로드 정책·오류 계약을 확정. 실제 검사 경로와 결과 전달·최소 권한을 설계하고 검사 결과를 DB·캐시·다운로드 판단에 반영. 정상·악성 시험 파일·검사 오류·시간 초과·이벤트 중복/역순·업로드 DB 저장 전 이벤트 도착·삭제와 검사 완료의 경합을 검증하고, 실패·미검사 파일이 정상으로 처리되거나 삭제 파일이 복구되지 않음을 확인. 확장자 가드·S3 태그만으로 실제 백신 검사 완료를 표시하지 않음. [AWS의 현재 한계](design/AWS.md#현재-한계)를 해소한 범위만 갱신하고 PRD·DESIGN·DATABASE·API·[이전 절차](design/MIGRATION.md)·배포·검증 기준과 실제 결과를 연결 |
| PERF-01 | P2 | 예정 | 재현 가능한 부하·스케일링 실험. VERIFY-02, OBS-02, IAC-02 선행 | 파일 크기·동시 사용자·요청 비율·기간·예열·반복 조건과 비용/중단 기준을 정해 실행. 처리량·오류율·p95·task 수·확장/축소 시간을 기록하고 동일 조건의 기준선과 비교. [최종 검증](design/VALIDATION.md)에 방법, 작업일지에 원본 결과 연결 |

## 4단계 - 비교·정리

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| ARCH-01 | P2 | 예정 | 비용·가용성·전송 경로 설계 비교. VERIFY-01, PERF-01 선행 | ECS/EKS, Aurora/RDS PostgreSQL, 단일/AZ별 NAT, S3 endpoint, ECS 중계/presigned URL의 요구사항·장애 영향·비용을 비교. 저비용 실습/가용성 강화 구성의 가정·리전·산정일·운영 부담을 명시하고 한 가지 개선은 전후 측정. 비교만으로 기술 교체를 확정하지 않고 미채택 대안은 [제안 목록](proposals/INDEX.md), 합의한 중요 결정은 [결정 목록](DECISIONS.md)에 연결 |
| PORT-02 | P2 | 예정 | 검증 근거 중심 포트폴리오 정리. IAC-03, CICD-03, OPS-01/02, PERF-01, ARCH-01 선행 | [포트폴리오](PORTFOLIO.md)·[루트 소개](../README.md)에 실제 구성·설계 이유·측정 결과·한계·증빙 링크를 반영. 재현 명령, 배포 실패 복구/장애 대응 시연, 비용 비교를 포함하고 미실행 항목은 별도 표시 |
| K8S-01 | P3 | 선택·보류 | Kubernetes 비교 확장 여부 검토. PORT-02 선행 | 목표 직무의 Kubernetes 필요성과 추가 비용·기간을 검토해 채택/보류 근거를 기록. 채택 시 동일 앱의 EKS 배포, Helm·Argo CD·probe·HPA·자원 제한·rollout 복구 수용 기준을 별도 작업으로 구체화. 현재 EKS 구현 착수나 상시 운영을 확정하지 않음 |

## 완료된 작업

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| LAYER-01 | P2 | 완료 (로컬 검증) | 인증 서비스의 API DTO 의존 제거. 선행 없음 | `AuthService`가 `auth.api`(`SignupRequest`·`LoginResponse`)와 `user.api`(`UserResponse`)를 import하지 않도록 정리. 회원가입은 값 또는 서비스 입력 타입으로 받고 로그인은 사용자·토큰·만료 정보를 담은 결과 타입을 반환하며, 응답 DTO 변환은 `AuthController`가 담당. [API 계약](design/API.md)의 요청·응답 형식과 오류 코드가 그대로인지 기존 인증 통합 테스트로 확인하고 `application → api` import가 없음을 확인. [아키텍처 페이지](assets/architecture-overview.html)의 예외 표기를 갱신 |
| LAYER-02 | P3 | 완료 (로컬 검증) | 서비스의 보안 타입 의존 제거. 선행 없음, LAYER-01과 함께 수행 가능 | `FileService` 등 application 서비스가 `config.security.AuthUserPrincipal`을 받지 않도록 사용자 ID·역할을 담은 보안 프레임워크 무관 타입 또는 값으로 전달하고, 컨트롤러에서 변환. 소유자·ADMIN 권한 판단과 인증 누락·권한 부족 응답이 변하지 않음을 기존 파일·사용자 API 통합 테스트로 확인하고 `application → config.security` import가 없음을 확인. 현재 구조로도 동작 문제는 없으므로 착수 여부는 LAYER-01 수행 시 함께 판단 |

## 권장 착수 순서

1. ENV-01 → ONP-01 → ONP-02로 온프레미스 기준선을 만든다.
2. ONP-03으로 FILE-01/02/05의 실서버 검증을 마치고, 같은 기간에 PORT-01과 MIG-01을 준비한다. IAC-02·CICD-01은 AWS 준비와 독립적으로 남은 실행 증빙을 확보할 수 있다.
3. PORT-01·MIG-01 완료 후 MIG-02로 배포를 실행하고(데이터 이전 없음, DEC-007) VERIFY-01로 비교한다. SEC-01·IAC-01은 MIG-02 전에 적용하는 것을 권장한다.
4. VERIFY-02 → CICD-02/03, OBS-01/02 → OPS-01/02 → PERF-01 순서로 운영 검증을 넓힌다. FILE-03은 FILE-01/02 실서버 검증 후 착수한다.
5. ARCH-01 → PORT-02로 마무리한다. K8S-01은 이후 선택 사항이다.

각 작업 종료 시 커밋·환경·명령·판정·한계·정리 여부와 증빙 경로를 작업일지에 연결하고 상태를 갱신한다.

## 실행 일정과 리스크

착수일·완료일과 AWS 비용 한도는 미정이다. 리전·인증·보존 범위는 PORT-01에서 정했다([DEC-006](DECISIONS.md#결정-목록), [DEC-008](DECISIONS.md#결정-목록)). 선행 조건과 예산을 확인한 뒤 이 문서의 각 작업에 일정을 기록한다. 로컬 호스트의 Docker 설치·메모리 부족은 ENV-01, AWS 권한 부족·생성 지연·데이터 불일치·OIDC 문제는 PORT-01과 해당 작업에서 추적한다.
