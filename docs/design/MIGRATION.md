온프레미스(로컬 Docker Compose)에서 AWS로 옮기는 전환 기준과 절차다. 실행 명령은 [배포](DEPLOYMENT.md), 검증은 [VALIDATION](VALIDATION.md), 작업 상태는 [TASKS](../TASKS.md#2단계---aws-마이그레이션)를 따른다. 이전 Day별 전환 기준은 [이전 문서 요약](../LEGACY.md)에 남겼다.

## 원칙

- 기능과 API·데이터 계약은 그대로 두고 실행 환경과 관리형 서비스만 바꾼다.
- 애플리케이션 코드는 바꾸지 않는다. 같은 이미지를 프로필과 환경변수만 바꿔 실행한다.
- 대상 구성은 현재 Terraform(`terraform/environments/dev`)이다([DEC-004](../DECISIONS.md#결정-목록)).
- 온프레미스 기준선(ONP-02)을 먼저 기록하고, 같은 시나리오로 AWS를 비교한다.

## 구성 대응

| 역할 | 온프레미스 | AWS | 애플리케이션 설정 변화 |
| --- | --- | --- | --- |
| 진입점 | nginx (본문 10MB 제한) | Route53·ACM(선택)·ALB | 없음 |
| 실행 | Compose `backend` 컨테이너 | ECS Fargate service, ECR 이미지 | 같은 이미지 |
| DB | PostgreSQL 16 컨테이너 | Aurora PostgreSQL Serverless v2 (database subnet) | `DB_URL`·계정 |
| 캐시 | Redis 7 컨테이너 | ElastiCache Redis (TLS) | `REDIS_HOST`, `REDIS_SSL_ENABLED=true` |
| 파일 저장 | MinIO 버킷 `files` | S3 버킷, 접두어 `files/` | 프로필 `minio` → `s3`, `S3_BUCKET`·`S3_OBJECT_PREFIX` |
| 후처리 | 없음 | S3 `ObjectCreated` → Lambda (확장자 가드·태그) | 없음 |
| 로그·지표 | `docker compose logs` | CloudWatch Logs·Alarms | 없음 |
| 배포 | `docker compose up --build` | GitHub Actions(OIDC) → ECR → ECS rolling | 없음 |

## 유지하는 계약

| 계약 | 기준 | 비고 |
| --- | --- | --- |
| API 요청·응답·오류 코드 | [API](API.md) | `Idempotency-Key`, 관리자 점검 API 포함 |
| 테이블 | `users`, `files`, `upload_requests` ([DATABASE](DATABASE.md)) | 스키마는 Hibernate `ddl-auto=update`가 생성 |
| 캐시 키·값 | `files:metadata:{fileId}`, 값에 `version` | TTL 5분, 이전 대상 아님 |
| `files.storage_path` | 저장소 adapter의 객체 키 | **환경마다 형식이 다르다** (아래) |

`storage_path` 형식:

- MinIO: `<uuid>.<확장자>` (버킷 루트)
- S3: `files/<uuid>.<확장자>`. 애플리케이션 IAM 정책과 Lambda 알림이 `files/` 접두어로 제한되어 있다.

따라서 기존 데이터를 옮길 때는 객체를 `files/` 아래로 복사하고 `files.storage_path`와 `files.stored_file_name`에 `files/`를 붙여야 한다. 새로 업로드하는 파일은 S3 adapter가 처음부터 `files/` 키를 쓴다.

## 전환 절차

MIG-01에서 **데이터를 옮기지 않기로** 정했다([DEC-007](../DECISIONS.md#결정-목록)). AWS는 빈 DB·빈 bucket으로 시작하고, 같은 검증 시나리오를 AWS에서 다시 실행해 계약을 비교한다. 아래 표에서 5·6단계와 V-MIG는 이번 범위에서 실행하지 않는다. 나중에 실제 데이터를 옮기게 되면 [데이터 이전 제안](../proposals/DATA-MIGRATION.md)의 B·C를 다시 검토하고 결정을 새로 기록한다.

| 단계 | 내용 | 완료 기준 | 작업 |
| --- | --- | --- | --- |
| 1. 기준선 | 온프레미스 기능·장애 시나리오 실행, 이전 대상 건수·용량 측정 | V-FUNC·V-DATA 통과, V-MIG-01 기준값 기록 | ONP-02, ONP-03 |
| 2. 준비 | 계정·예산·정리 조건, 비밀값 주입, 원격 state, 데이터 이전 방식 결정 | PORT-01 기록, MIG-01 결정(이전 없음) | PORT-01, SEC-01, IAC-01, MIG-01 |
| 3. 인프라 생성 | 유지 계층(`dev-data`: VPC·S3·Aurora) → 실습 계층(`dev`: NAT·ECS·ElastiCache·Lambda 등) 순서로 `terraform apply` | output 확인, 비용 알림 동작 | MIG-02 |
| 4. 애플리케이션 배포 | CD로 이미지 push·ECS 배포 | V-AWS-01 통과(빈 DB로 health·가입·업로드 smoke) | MIG-02 |
| 5. 쓰기 중지 | (이번 범위 제외) 온프레미스 업로드·삭제·가입 차단, 최종 백업 | 백업 파일·객체 미러 해시 기록 | - |
| 6. 데이터 이전 | (이번 범위 제외) DB 복원, 객체 복사, `storage_path` 접두어 변환 | V-MIG-02 통과 | - |
| 7. 비교 검증 | 같은 시나리오를 AWS에서 실행 | V-AWS-02~03, VERIFY-01 기준 충족 | VERIFY-01 |
| 8. 전환 | 사용자 진입점을 AWS로 바꿈(도메인 사용 시 Route53) | health·smoke 통과 | MIG-02 |
| 9. 안정화 | 장애 주입·알람·성능 smoke | V-AWS-04~06, V-PERF-01 | VERIFY-02 |

아래는 나중에 데이터를 옮길 때 쓸 6단계 참고 절차다. 6단계의 공통 SQL(복원 후 Aurora에서 실행):

```sql
BEGIN;
UPDATE files SET storage_path = 'files/' || storage_path, stored_file_name = 'files/' || stored_file_name
 WHERE storage_path NOT LIKE 'files/%';
SELECT count(*) FROM files WHERE storage_path NOT LIKE 'files/%';  -- 0이어야 함
COMMIT;
```

객체 복사(로컬 미러 → S3):

```bash
aws s3 sync backup/minio-files "s3://$FILE_BUCKET/files/" --region "$AWS_REGION"
```

DB 복원 경로(로컬에서 Aurora로 가는 방법)는 정하지 않았다. 데이터 이전을 다시 범위에 넣을 때 [데이터 이전 제안](../proposals/DATA-MIGRATION.md)의 대안 중에서 정한다.

## 이전하지 않는 데이터

- `upload_requests`: 24시간 보관 기록이다. 이전 후 같은 키의 재전송은 새 요청으로 처리된다.
- Redis 캐시: 비어 있는 상태로 시작하고 조회 시 다시 채워진다.
- 고아 객체: 이전 전에 [V-ADMIN-02](VALIDATION.md#v-admin-02-고아-객체-보고와-정리)로 정리한다.

## 주의 사항

- S3에 객체를 복사하면 객체마다 Lambda가 실행되어 태그를 기록한다. 객체 수가 많으면 호출 비용과 시간을 확인한다.
- 시퀀스: `pg_dump -Fc`/`pg_restore`는 `id` 시퀀스 값을 함께 옮긴다. 복원 후 새 업로드의 `fileId`가 기존 최대값보다 큰지 확인한다(V-MIG-02).
- Aurora 엔진 버전은 Terraform 변수(`aurora_engine_version`)를 따른다. 온프레미스 PostgreSQL 16 덤프를 복원할 수 있는 버전인지 확인한다.
- 이전 중 생긴 온프레미스 쓰기는 옮겨지지 않는다. 쓰기 중지(5단계)를 먼저 확인한다.
- 온프레미스 PostgreSQL은 항상 떠 있지만 Aurora는 0 ACU로 일시정지될 수 있다. backend는 기동 중 DB 연결에 실패하면 종료되므로, 실행 중인 task가 없을 때는 배포 전에 DB를 깨운다([DEC-010](../DECISIONS.md#결정-목록), [배포 전 DB 깨우기](DEPLOYMENT.md#46-github-actions-배포-실행)).

## 롤백

- 데이터를 옮기지 않으므로 온프레미스 데이터는 그대로 남는다. 롤백은 실습 계층(`dev`)을 `terraform destroy`하는 것으로 충분하다. 유지 계층의 AWS 데이터는 검증 중 생긴 데이터만 담는다.
- 진입점을 AWS로 바꾸는 8단계는 도메인을 쓸 때만 의미가 있다. 되돌릴 때는 DNS 레코드를 온프레미스로 돌린다.

## 미정

- (데이터 이전을 다시 범위에 넣을 때) DB 복원 경로, 쓰기 중지 방법, 허용 중단 시간
- 비밀값 주입 방식 (SEC-01)
- 이전 후 온프레미스 데이터 보존 기간
