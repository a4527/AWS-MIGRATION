- 상태: B + B-2 채택(2026-11-19, [DEC-012](../DECISIONS.md#결정-목록), [ADR-002](../adr/002-CROSS-ACCOUNT-BACKUP.md)). A·C·D·B-1은 미채택으로 보존. 구현은 BACKUP-01. 2026-11-24 철거([DEC-025](../DECISIONS.md#결정-목록)): 검증이 끝났고 시험 데이터뿐이라 복제·dump task·개인 계정 bucket을 지웠다. 아래 비교는 당시 판단으로 보존한다.
- 작성일: 2026-11-19
- 관련: [OPS-02](../TASKS.md#3단계---운영-검증보강), [DEC-007](../DECISIONS.md#결정-목록)·[DEC-008](../DECISIONS.md#결정-목록)·[DEC-009](../DECISIONS.md#결정-목록), [작업일지](../worklogs/2026-11-19.md#개인-계정-백업-준비)

## 문제

AWS 실습 계정은 부트캠프 공유 계정이다. 계정 정리 정책이 Owner 태그 없는 리소스를 지우고, 사용자 확인으로는 그 밖에도 "가끔 지울 때가 있다". 유지 계층(`dev-data`)의 Aurora 데이터와 S3 객체가 사라질 수 있다. 같은 계정 안의 스냅샷·버전도 같은 정리 대상이 될 수 있다.

## 근거 (현재 구성)

- 데이터는 검증 시나리오로 만든 시험 데이터뿐이다([DEC-007](../DECISIONS.md#결정-목록)). 인프라는 Terraform으로 다시 만들 수 있고, 잃는 것은 데이터 재생성 시간이다.
- 앱은 실습 계층이 떠 있을 때만 실행되므로 DB 데이터는 실습 중에만 바뀐다.
- Aurora는 AWS 관리 키(`aws/rds`)로 암호화되어 있다(2026-11-19 `describe-db-clusters` 확인). AWS 관리 키로 암호화된 스냅샷은 다른 계정에 공유할 수 없다.
- S3 file bucket은 버전 관리가 켜져 있어 복제 규칙을 걸 수 있다.
- 개인 계정은 부트캠프 조직 밖이라 AWS Backup 교차 계정 복사(같은 Organizations 필요)와 RAM 공유는 쓰기 어렵다.

## 대안

| 대안 | 방법 | 장점 | 단점 |
| --- | --- | --- | --- |
| A. 한 계정, 재생성 대비 | Owner 태그 유지, 지워지면 Terraform·시나리오로 다시 만듦 | 추가 작업·비용 없음 | 데이터 보존 없음 |
| B. 한 계정 + 개인 계정 백업 | 실행은 부트캠프 계정, 사본만 개인 계정으로 | 앱·네트워크 변경 없음 | 백업 시점까지만 보존, 복원 수동 |
| C. 유지 계층만 개인 계정 | Aurora·S3·비밀값을 개인 계정에 두고 ECS 등은 부트캠프 계정에서 교차 계정 접근 | 데이터가 계정 정리와 무관 | VPC peering·교차 계정 SG·bucket policy·Lambda 이벤트·비밀값 공유(SSM Advanced+RAM 또는 Secrets Manager+CMK)·provider 두 벌. MIG-02 전 대규모 변경 |
| D. 전부 개인 계정 | 두 계층을 개인 계정에 apply | 구조 그대로, 정리 정책 없음 | 실습 비용(NAT·ALB·ECS·Redis)을 본인 부담 |

B의 DB 백업 방식:

| 방식 | 방법 | 비용 | 특징 |
| --- | --- | --- | --- |
| B-1. 스냅샷 재암호화 공유 | 수동 스냅샷 → 고객 관리 KMS 키로 복사 → 개인 계정에 공유 → 개인 계정에서 복사 | KMS 키 월 약 $1(부트캠프 예산 포함) + 스냅샷 저장 | 클러스터 단위 복원 |
| B-2. `pg_dump` → S3 | 실습 종료 전 ECS 일회성 task로 `pg_dump` → file bucket 전용 접두어 → S3 복제로 개인 계정 | 사실상 $0 | KMS 불필요, 백업 경로를 S3 하나로 통일. 실습 계층이 있을 때만 실행, 복원 시 다시 적재 |

S3 객체는 개인 계정 bucket(`fileshare-backup-*`)으로 교차 계정 복제(같은 리전, 삭제 마커 복제 안 함)한다. 복제는 부트캠프 계정의 복제 role(trust: `s3.amazonaws.com`, `aws:SourceAccount`·`aws:SourceArn` 조건)이 하고, 개인 계정 bucket은 bucket policy로 그 role만 허용한다. 규칙 이전 객체는 따로 한 번 복사한다.

## 권장

B + B-2. 비용이 거의 없고 백업이 계정 정리와 무관한 곳에 남는다. OPS-02(백업 복원 검증)의 일부를 겸한다. C는 교차 계정 경험 자체가 목표일 때 ARCH-01의 별도 과제로 두고, 부트캠프 이후 계속 실습하면 D로 옮긴다.

## 영향

- Terraform: 부트캠프 계정에 복제 role·복제 규칙·`pg_dump` task 정의, 개인 계정에 백업 bucket과 bucket policy(별도 루트 모듈, provider 프로필 `personal-backup-tf`). 개인 계정 ID는 gitignore된 tfvars에만 둔다.
- 애플리케이션 IAM·Lambda 알림은 `files/` 접두어로 제한되어 있으므로 dump 접두어는 그 밖에 두고, Lambda가 dump에 반응하지 않는지 확인한다.
- `pg_dump` task는 DB 비밀번호가 필요해 SEC-01(SSM SecureString) 뒤에 만든다.

## 준비된 것 (2026-11-19)

개인 계정에 IAM user `fileshare-backup-admin`(권한: 아래 role 맡기, `SignInLocalDevelopmentAccess`)과 role `fileshare-backup-bucket-admin`(trust: 그 user + MFA, 권한: `fileshare-backup-*` bucket 관리만)을 만들었다. 로컬 프로필:

| 프로필 | 자격 증명 |
| --- | --- |
| `personal` | `aws login`(콘솔 로그인, 최대 12시간) |
| `personal-backup` | `personal`로 role을 맡음, `mfa_serial`로 MFA 코드 입력 |
| `personal-backup-tf` | `credential_process`로 `personal-backup`의 캐시 자격 증명을 Terraform에 넘김 |

## 미정

2026-11-23 BACKUP-01에서 정했다([DEC-022](../DECISIONS.md#결정-목록)): B-2 채택, dump는 실습 종료마다·현재 버전 무기한 보존, 이전 버전 90일 만료, 복원 시험 계정은 OPS-02에서 정한다. 아래는 당시 기록이다.


- B-2 채택 여부, dump 주기(실습 종료마다)와 보존 기간, 개인 계정 bucket 수명주기
- 복원 시험을 어느 계정에서 할지(개인 계정 DB 복원이면 role 권한 추가 필요)

## 다음 확인

- 채택 후 BACKUP-01로 등록했다. 순서는 SEC-01 → MIG-02 → BACKUP-01(S3 복제는 먼저 해도 됨). 위 미정 항목은 BACKUP-01 착수 시 정한다.
