작업 상태의 기준 문서다. Day 1~14의 기존 `[o]`는 산출물 완료 표기를 보존한 것이며 실제 AWS 실행 성공을 일괄 보증하지 않는다. 현재 범위는 [진행 요약](PROGRESS.md), 수행 근거는 [작업일지](worklogs/LEGACY.md)를 참조한다. 특히 Day 13 완료 범위는 검증 절차·판정 기준 작성이다.

## Day 1 - 요구사항 및 설계

- [o] 요구사항 정리
- [o] 서비스 도메인 및 권한 모델 정의
- [o] 파일 라이프사이클 정의
- [o] API 초안 작성
- [o] PostgreSQL 스키마 및 ERD 초안 작성
- [o] 온프레미스 아키텍처 초안 작성

## Day 2 - Spring Boot 프로젝트 골격

- [o] Spring Boot 프로젝트 생성
- [o] 패키지 구조 설계
- [o] 공통 응답 포맷 정의
- [o] 예외 처리 구조 구성
- [o] JWT 보안 설정 골격 작성

## Day 3 - 인증 및 사용자 관리

- [o] 회원가입 구현
- [o] 로그인 구현
- [o] JWT 발급/검증 구현
- [o] 사용자 조회 API 구현
- [o] 권한 기반 접근 제어 적용

## Day 4 - 파일 CRUD

- [o] 업로드 API 구현
- [o] 다운로드 API 구현
- [o] 삭제 API 구현
- [o] 메타데이터 저장 로직 구현
- [o] 파일 확장자/크기 검증

## Day 5 - PostgreSQL, Redis, MinIO 연동

- [o] PostgreSQL 테이블 확정
- [o] JPA 엔티티 및 Repository 작성
- [o] Redis 캐시 적용
- [o] MinIO 연동
- [o] 로컬 파일 저장소 대체 확인

## Day 6 - Docker Compose 및 Nginx

- [o] Dockerfile 작성
- [o] Docker Compose 구성
- [o] Nginx reverse proxy 설정
- [o] 환경변수 분리
- [o] 로컬 통합 실행 검증

## Day 7 - 온프레미스 검증 및 문서화

- [o] 기능 테스트
- [o] 장애 시나리오 점검
- [o] API 문서 정리
- [o] 트러블슈팅 초안 작성
- [o] 온프레미스 아키텍처 문서 마무리

## Day 8 - AWS 인프라 설계 및 Terraform 시작

- [o] VPC 설계
- [o] ECS, ECR, IAM, ALB, Route53, ACM 범위 정의
- [o] Terraform 프로젝트 구조 생성
- [o] 환경별 변수 체계 설계

## Day 9 - ECS 및 데이터/스토리지 이전

- [o] ECS 클러스터와 Fargate 서비스 배포
- [o] Aurora PostgreSQL 구성
- [o] ElastiCache 계획 반영
- [o] S3 업로드 구조로 전환
- [o] Secrets 및 IAM 정책 정리

## Day 10 - ECS 배포 및 CI/CD

- [o] ECS task definition 배포 흐름 작성
- [o] GitHub Actions CI/CD 구축
- [o] ECR 빌드/푸시 연동
- [o] ECS service 배포 자동화

## Day 11 - Lambda, 모니터링, 스케일링

- [o] S3 이벤트 기반 Lambda 구성
- [o] 바이러스 검사 및 메타데이터 추출 후처리 구현
- [o] CloudWatch 로그/지표 설정
- [o] Auto Scaling 정책 구성
- [o] 헬스체크 및 롤링 배포 확인

## Day 12 - HTTPS 및 보안 강화

- [o] Route53 도메인 연결
- [o] ACM 인증서 적용
- [o] ALB HTTPS 구성
- [o] IAM 최소 권한 원칙 점검
- [o] 공개/비공개 리소스 분리 확인

## Day 13 - 최종 검증 및 성능 점검

- [o] 온프레미스 vs AWS 동작 비교
- [o] 업로드/다운로드 시나리오 테스트
- [o] 장애 복구 시나리오 테스트
- [o] 로그 및 알람 확인

## Day 14 - 포트폴리오 및 마감

- [o] README 정리
- [o] 프로젝트 스토리 작성
- [o] 기술 선택 이유 정리
- [o] 결과 이미지/다이어그램 정리
- [o] `docs/PORTFOLIO.md` 완성

## 후속 검증 작업

기존 Day별 완료 이력을 바꾸지 않고 실행 결과가 필요한 확인을 추적한다. 아래 상태는 이번 문서 정리에서 확인 가능한 근거를 기준으로 한다.

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| VERIFY-01 | 1 | 실행 결과 미확인 | 온프레미스/AWS API·데이터 계약 비교. AWS 인증·변수·비용 범위와 배포 환경 준비 필요 | [AWS 런북](design/DEPLOYMENT.md) 배포 후 [최종 검증](design/VALIDATION.md)의 기능·파일·저장소 시나리오를 수행하고 양쪽 응답·파일 비교·object key/cache key 결과를 당일 작업일지에 연결 |
| VERIFY-02 | 2 | 실행 결과 미확인 | 장애 주입·복구·smoke 성능 점검. VERIFY-01 환경 및 관측·복구 수단 준비 필요 | [최종 검증](design/VALIDATION.md)의 ECS/Lambda 실패 주입, 로그·알람, 성능 절차 결과와 한계·복구·정리 여부를 당일 작업일지에 연결 |

운영 보강 후보의 기존 근거는 [인프라 문서의 현재 한계](design/AWS.md#현재-한계)에 보존한다. 사용자 요청으로 등록한 후속 범위는 아래에서 추적한다.


## 포트폴리오 보강 작업

2026-11-16 사용자 요청으로 등록했다. 작업 등록은 구현·배포·검증 완료를 뜻하지 않는다. 기존 VERIFY-01/02를 재사용하며 Day별 완료 이력은 유지한다. P0은 실행 준비, P1은 핵심 보강, P2는 비교·결과 정리, P3은 선택 확장이다. 같은 우선순위 안에서는 선행 조건을 따른다. 착수일·완료일과 AWS 비용 한도는 미정이며, 일정과 실행 순서는 이 문서에서 관리한다.

### 실행 준비와 IaC

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| PORT-01 | P0 | 예정 | AWS 검증 환경·예산·실험 범위 준비. 선행 없음 | 계정·리전·인증 방식, 실습 데이터, 비용 한도·알림, 리소스 보존/삭제 범위, 실험 중단·복구 조건을 비밀값 없이 기록하고 [AWS 런북](design/DEPLOYMENT.md)에 반영. VERIFY-01/02 실행 전에 확인 |
| IAC-01 | P1 | 예정 | 원격 state와 환경 분리 보강. PORT-01 선행 | state bootstrap·접근 권한·암호화·버전 관리·잠금·복구 절차를 구현하고 동시 실행 잠금과 복구를 검증. Terraform 버전과 S3/DynamoDB 잠금 방식의 호환성을 확인하고 환경별 state 격리를 검증. [배포 기준](design/DEPLOYMENT.md)·[Terraform 안내](../terraform/README.md)에 반영 |
| IAC-02 | P1 | 예정 | Terraform과 CD·Auto Scaling의 관리 책임 정리. 선행 없음 | task definition·service 배포 버전·desired count의 관리 주체와 최초 배포 순서를 정의. CD 배포·스케일 변경 후 plan/apply가 이를 의도치 않게 되돌리지 않는지 검증하고 [아키텍처](DESIGN.md)·[배포 기준](design/DEPLOYMENT.md)에 반영 |
| IAC-03 | P1 | 예정 | Terraform PR 검증·승인 적용·drift 탐지. IAC-01/02 선행 | fmt/validate·보안 검사·plan 검토·승인 후 검토한 plan 적용 흐름 구현. 읽기/적용 권한과 신뢰된 PR 경계를 분리하고 plan/state 비밀값 노출 방지. 정기 plan으로 의도적 drift 탐지 후 검토·복구를 검증하고 [AWS 런북](design/DEPLOYMENT.md)에 반영 |
| SEC-01 | P1 | 예정 | 비밀값 주입과 배포 권한 분리. PORT-01 선행 | DB 비밀번호·JWT secret을 Secrets Manager 참조로 주입하고 task definition 일반 환경변수에서 제거. execution/task/CI role의 필요한 권한과 OIDC 신뢰 조건을 검증. Terraform sensitive 표기만으로 state의 비밀값이 제거되지 않음을 고려해 생성·저장 경로를 점검하고 로그·plan 노출 여부를 확인. [배포 기준](design/DEPLOYMENT.md)에 반영 |

### CI/CD와 관측성

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| CICD-01 | P1 | 구현·정적 검증, 실행 검증 대기 | PR CI와 이미지 검증. 선행 없음 | PR에서 백엔드 테스트·이미지 빌드·취약점 검사 수행. 차단 기준과 예외 처리 방식을 정의하고 실패 시 배포 차단을 확인. [구현·검증 근거](worklogs/2026-11-16.md#cicd-01-pr이미지-검증-구현)의 미확인 항목과 GitHub 실행 증빙을 보완해야 완료. AWS 배포 권한은 배포 job에 한정하고 [배포 기준](design/DEPLOYMENT.md)에 반영 |
| CICD-02 | P1 | 예정 | 이미지 승격·환경 승인·배포 후 smoke test. VERIFY-01, IAC-02, SEC-01, CICD-01 선행 | 검증한 동일 image digest로 테스트 환경 검증과 배포 대상 환경 승격을 수행. 환경별 승인·동시 실행 제어·OIDC 조건을 적용하고 로그인/업로드/다운로드 smoke 실패를 배포 실패로 처리. 커밋·digest·task revision을 추적 가능하게 기록 |
| OBS-01 | P1 | 예정 | 서비스 지표·구조화 로그·대시보드. VERIFY-01 선행 | API별 성공률·p95·요청량과 ALB target health, ECS, DB, Lambda 지표를 연결. 요청 ID로 API 로그 추적을 시연하고 비밀값·파일 내용은 제외. 조회/전송의 SLI·SLO 대상·기간·임계값을 측정 근거로 정의하며 짧은 실험을 장기 가용성 실적으로 표시하지 않음. [최종 검증](design/VALIDATION.md)에 반영 |
| OBS-02 | P1 | 예정 | 알람 전달과 대응 런북. OBS-01 선행 | CloudWatch alarm action과 알림 수신 경로를 구성하고 의도적 오류의 알람 발화·수신·정상 복귀를 확인. 알람별 증상·진단·복구·에스컬레이션 기준을 [AWS 런북](design/DEPLOYMENT.md)·[트러블슈팅](TROUBLESHOOTING.md)에 연결 |
| CICD-03 | P1 | 예정 | 실패 배포 감지·롤백. CICD-02, OBS-02 선행 | ECS 기동/health 실패와 기동 후 오류율 증가에 대한 감지·롤백 방식 구성. 의도적 실패 배포에서 이전 정상 revision 복구와 API 정상화를 검증하고 감지·복구 시간 및 자동/수동 복구 한계를 기록 |

### 운영 실험과 결과 정리

| ID | 우선순위 | 상태 | 작업·선행 조건 | 수용 기준·근거 |
| --- | --- | --- | --- | --- |
| OPS-01 | P1 | 예정 | 장애 대응 훈련·사후 분석. VERIFY-02, OBS-02, CICD-03 선행 | 기존 VERIFY-02 결과를 재사용하고 ECS task 종료·실패 배포에 대한 타임라인, 사용자 영향, 감지/복구 시간, 원인·개선점을 기록. 캐시 장애 시 실제 API 동작과 복구 한계를 확인. 실험 중단·복구·잔여 리소스를 확인하고 [최종 검증](design/VALIDATION.md)·당일 작업일지에 연결 |
| OPS-02 | P1 | 예정 | DB·object storage 백업 복원 검증. VERIFY-01, SEC-01 선행 | 백업 보존·삭제 보호 정책과 RTO/RPO 목표를 정의. 별도 환경에 DB 백업 복원 후 storage_path와 S3 object/version 정합성 및 실제 다운로드 해시를 확인. 복원 소요·데이터 손실 범위·목표 충족 여부·정리 결과를 기록하고 [AWS 런북](design/DEPLOYMENT.md)·[최종 검증](design/VALIDATION.md)에 반영 |
| PERF-01 | P2 | 예정 | 재현 가능한 부하·스케일링 실험. VERIFY-02, OBS-02, IAC-02 선행 | 파일 크기·동시 사용자·요청 비율·기간·예열·반복 조건과 비용/중단 기준을 정해 실행. 처리량·오류율·p95·task 수·확장/축소 시간을 기록하고 동일 조건의 기준선과 비교. [최종 검증](design/VALIDATION.md)에 방법, 작업일지에 원본 결과 연결 |
| ARCH-01 | P2 | 예정 | 비용·가용성·전송 경로 설계 비교. VERIFY-01, PERF-01 선행 | ECS/EKS, Aurora/RDS PostgreSQL, 단일/AZ별 NAT, S3 endpoint, ECS 중계/presigned URL의 요구사항·장애 영향·비용을 비교. 저비용 실습/가용성 강화 구성의 가정·리전·산정일·운영 부담을 명시하고 한 가지 개선은 전후 측정. 비교만으로 기술 교체를 확정하지 않고 미채택 대안은 [제안 목록](proposals/INDEX.md), 합의한 중요 결정은 [결정 목록](DECISIONS.md)에 연결 |
| PORT-02 | P2 | 예정 | 검증 근거 중심 포트폴리오 정리. IAC-03, CICD-03, OPS-01/02, PERF-01, ARCH-01 선행 | [포트폴리오](PORTFOLIO.md)·[루트 소개](../README.md)에 실제 구성·설계 이유·측정 결과·한계·증빙 링크를 반영. 재현 명령, 배포 실패 복구/장애 대응 시연, 비용 비교를 포함하고 미실행 항목은 별도 표시 |
| K8S-01 | P3 | 선택·보류 | Kubernetes 비교 확장 여부 검토. PORT-02 선행 | 목표 직무의 Kubernetes 필요성과 추가 비용·기간을 검토해 채택/보류 근거를 기록. 채택 시 동일 앱의 EKS 배포, Helm·Argo CD·probe·HPA·자원 제한·rollout 복구 수용 기준을 별도 작업으로 구체화. 현재 EKS 구현 착수나 상시 운영을 확정하지 않음 |

### 권장 착수 순서와 증빙

1. PORT-01을 먼저 수행하고 VERIFY-01을 완료한다. IAC-02와 CICD-01은 AWS 환경 준비와 독립적으로 착수할 수 있다.
2. IAC-01/02, SEC-01, IAC-03, CICD-01/02와 OBS-01/02를 선행 조건에 맞춰 진행한다.
3. 관측·복구 수단을 준비한 뒤 VERIFY-02를 수행하고 CICD-03, OPS-01/02로 운영 검증을 확장한다.
4. PERF-01 → ARCH-01 → PORT-02 순서로 측정·설계 근거·외부 설명을 완성한다. K8S-01은 이후 선택 사항이다.

실행 근거는 [당일 작업일지](worklogs/2026-11-16.md#포트폴리오-보강-작업-등록)를 시작점으로 실제 수행일의 작업일지에 누적한다. 각 작업 종료 시 커밋·환경·명령·판정·한계·정리 여부와 증빙 경로를 연결하고 상태를 갱신한다. AWS 배포·부하·장애 실험은 이번 작업 등록에서 수행하지 않았다.


## 실행 일정과 리스크

착수일·완료일은 미정이다. 선행 조건과 예산을 확인한 뒤 이 문서의 각 작업에 일정을 기록한다. AWS 권한 부족, 생성 지연, 데이터 불일치, OIDC·배포 권한 문제는 PORT-01과 해당 검증 작업에서 추적한다. 최초 2주 계획은 [과거 이력](worklogs/LEGACY.md#최초-일정-계획-보관)에 보관한다.
