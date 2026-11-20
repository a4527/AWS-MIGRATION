- 상태: 대안 A 채택(2026-11-18, [DEC-007](../DECISIONS.md#결정-목록)). B·C·D는 미채택으로 보존
- 작성일: 2026-11-17
- 관련: [MIG-01](../TASKS.md#2단계---aws-마이그레이션), [전환 절차](../design/MIGRATION.md#전환-절차)

## 문제

온프레미스(로컬 Compose)에 쌓인 사용자·파일 메타데이터와 파일 객체를 AWS로 옮기는 방법이 정해지지 않았다. 현재 구성에는 두 가지 제약이 있다.

- Aurora는 인터넷 라우트가 없는 database subnet에 있고 application SG에서만 5432를 허용한다. 로컬 PC에서 바로 접속할 수 없다.
- 객체 키 형식이 다르다. MinIO adapter는 `<uuid>.<ext>`, S3 adapter는 `files/<uuid>.<ext>`를 `storage_path`로 쓴다. 애플리케이션 IAM 정책과 Lambda 알림도 `files/` 접두어로 제한되어 있다.

## 근거 (현재 코드·구성)

- 스키마는 Hibernate `ddl-auto=update`로 생성된다. 별도 마이그레이션 도구(Flyway 등)는 없다.
- 이전 대상 테이블: `users`, `files`. `upload_requests`는 24시간 보관 기록이라 이전하지 않아도 된다. Redis 캐시는 이전하지 않는다.
- S3에 객체를 복사하면 `ObjectCreated` 이벤트로 Lambda가 객체마다 실행되어 태그를 기록한다.

## 대안

| 대안 | 방법 | 장점 | 단점 |
| --- | --- | --- | --- |
| A. 데이터 이전 없음 | AWS를 빈 상태로 배포하고 같은 시나리오를 다시 실행해 계약만 비교 | 가장 단순, Terraform 변경 없음 | 실제 데이터 마이그레이션 경험·검증이 없음 |
| B. ECS 일회성 작업으로 복원 | `pg_dump` 파일을 S3 이전 전용 접두어에 올리고, 같은 VPC의 ECS 일회성 task(PostgreSQL 클라이언트 이미지)가 Aurora에 복원. 객체는 `mc mirror`로 받아 `aws s3 sync`로 `files/` 아래에 업로드한 뒤 `storage_path`·`stored_file_name`에 `files/` 접두어를 붙이는 SQL 실행 | 상시 접속 경로를 열지 않음, 기존 네트워크 경계 유지 | 일회성 task 정의·이전 접두어용 IAM 권한 추가 필요, 실행 로그 확인이 번거로움 |
| C. SSM 포트 포워딩 bastion | private subnet에 SSM Agent가 있는 작은 EC2를 두고 Session Manager 포트 포워딩으로 로컬에서 `psql`·`pg_restore` 실행. 객체 이전은 B와 같음 | 로컬에서 직접 조회·검증 가능, 검증 시 DB 확인에도 재사용 | EC2·IAM·SG 추가, 이전 후 제거 관리 필요 |
| D. AWS DMS | 복제 인스턴스로 PostgreSQL → Aurora 전체·변경분 복제 | 무중단 전환 가능 | 로컬 DB를 AWS에서 접근 가능하게 해야 하고 이 규모에 과함 |

## 영향

- B·C는 Terraform 변경이 필요하다(일회성 task 정의 또는 bastion). 변경은 MIG-01 결정 후 IAC 범위로 추가한다.
- 접두어 변환 대신 S3 adapter의 `S3_OBJECT_PREFIX`를 비우는 방법도 있으나, IAM·Lambda 접두어 제한을 넓혀야 하므로 보안 경계가 바뀐다. 권장하지 않는다.
- 이전 중에는 온프레미스 쓰기를 멈춰야 한다(업로드·삭제·회원가입). 멈춘 시간이 전환 중단 시간이 된다.
- 롤백: 이전 전 `pg_dump`와 MinIO 볼륨을 보존하면 온프레미스로 돌아갈 수 있다. AWS에서 새로 생긴 쓰기는 롤백 시 잃는다.

## 결정 결과

2026-11-18 MIG-01에서 사용자가 A(데이터 이전 없음)를 선택했다. 이전 대상이 검증으로 만든 데이터(사용자 3명·파일 8건, 약 12MB)뿐이라 옮길 가치가 낮고, AWS에서 같은 시나리오를 다시 실행해 계약을 비교하는 것으로 VERIFY-01을 충족한다. Terraform 변경과 bastion 비용이 없다. 대신 실제 데이터 마이그레이션(덤프·복원·접두어 변환)의 실행 경험과 V-MIG 증빙은 남지 않는다.

## 미정

아래는 데이터 이전을 다시 범위에 넣을 때 정한다.

- 대안 선택(당시 권장: 검증 재사용성이 큰 C, 상시 경로를 피하려면 B).
- 쓰기 중지 방법(nginx에서 쓰기 메서드 차단 등)과 허용 중단 시간.
- 이전 후 온프레미스 데이터 보존 기간.

## 다음 확인

- ONP-02 완료 후 이전 대상 건수·용량을 측정한다.
- 선택한 대안의 Terraform 변경 범위와 비용을 PORT-01 예산과 대조한다.
