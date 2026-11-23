- 상태: Accepted (2026-11-23 구현, 세부 결정 [DEC-022](../DECISIONS.md#결정-목록))
- 결정일: 2026-11-19
- 합의 근거: 2026-11-19 대화에서 사용자가 "개인 계정 백업 제안도 채택할게"로 [제안](../proposals/CROSS-ACCOUNT-BACKUP.md)의 권장안(B + B-2)을 채택했다. [작업일지](../worklogs/2026-11-19.md#개인-계정-백업-채택)
- 관련 작업: [BACKUP-01](../TASKS.md#2단계---aws-마이그레이션), [OPS-02](../TASKS.md#3단계---운영-검증보강)

## 배경

AWS 실습 계정은 부트캠프 공유 계정이고 정리 정책이 리소스를 가끔 지운다. 유지 계층(`dev-data`)의 Aurora 데이터와 S3 객체를 잃을 수 있고, 같은 계정의 스냅샷·버전도 안전하지 않다. 데이터는 시험 데이터뿐이지만(DEC-007) 다시 만드는 비용을 줄이고 계정 정리와 무관한 사본을 두기로 했다.

## 결정

- 실행 구성은 부트캠프 계정 한 곳에 그대로 둔다. 개인 AWS 계정은 백업 사본만 받는다(실행 중 교차 계정 접근 없음).
- S3 file bucket → 개인 계정 bucket(`fileshare-backup-*`, 같은 리전)으로 교차 계정 복제한다. 삭제 마커는 복제하지 않는다. 복제는 부트캠프 계정의 복제 role(trust: `s3.amazonaws.com`, `aws:SourceAccount`·`aws:SourceArn` 조건)이 하고, 개인 계정 bucket은 bucket policy로 그 role만 허용한다. 규칙 이전 객체는 한 번 따로 복사한다.
- DB는 실습 종료 전 ECS 일회성 task로 `pg_dump`를 떠 file bucket의 `files/` 밖 전용 접두어에 올리고, 같은 복제로 개인 계정에 보낸다.
- 개인 계정 작업은 IAM user(`aws login`) → MFA 조건 role `fileshare-backup-bucket-admin`(권한: `fileshare-backup-*` bucket만)으로 한다. Terraform은 `credential_process` 프로필로 그 role을 쓴다. 개인 계정 ID는 gitignore된 tfvars에만 둔다.
- 순서: SEC-01 → MIG-02 → BACKUP-01. `pg_dump` task는 SSM의 DB 비밀번호와 실습 계층이 필요하다. S3 복제는 선행 없이 먼저 해도 된다.

## 대안

[제안 문서](../proposals/CROSS-ACCOUNT-BACKUP.md#대안)에 비교를 보존한다.

- 유지 계층만 개인 계정(실행 중 교차 계정): VPC peering, 교차 계정 SG·bucket policy·Lambda 이벤트, 비밀값 공유(SSM Advanced+RAM 또는 Secrets Manager+CMK), provider 두 벌이 필요해 MIG-02 전 변경이 크다.
- 스냅샷 재암호화 공유: Aurora가 `aws/rds` 관리 키라 고객 관리 KMS 키(월 약 $1)로 다시 복사해야 한다. 클러스터 단위 복원은 쉽지만 비용과 키 관리가 늘어난다.
- 전부 개인 계정: 정리 정책은 없지만 실습 비용을 본인이 낸다. 부트캠프 이후 계속 실습할 때 다시 검토한다.

## 영향

- 비용: 같은 리전 복제라 전송료가 없고, 시험 데이터 규모에서 저장·요청 비용은 사실상 $0이다. 개인 계정 비용은 부트캠프 예산(DEC-009) 밖이다.
- 보안 경계: 시험 데이터 사본이 조직 밖 개인 계정으로 나간다. dump에는 사용자 이메일과 비밀번호 해시가 들어 있으므로 개인 계정 bucket은 퍼블릭 차단·암호화·버전 관리를 켜고 접근을 MFA role로 제한한다.
- Terraform: 부트캠프 계정(복제 role·규칙, dump task 정의)과 개인 계정(백업 bucket·policy) 양쪽 코드가 생긴다. 애플리케이션 IAM과 Lambda 알림은 `files/` 접두어로 제한되어 있어 dump 접두어와 분리된다. Lambda가 dump에 반응하지 않는지 확인한다.
- 한계: 백업 시점(실습 종료) 이후 변경은 보존되지 않는다. 복원은 수동이며, 복원 검증은 OPS-02에서 한다. `personal-backup`은 1시간마다 MFA 코드를 다시 넣어야 한다.
