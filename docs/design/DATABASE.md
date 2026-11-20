## 데이터 모델 원칙

- 사용자, 파일, 권한, 운영 로그를 분리해서 관리한다.
- 파일 바이너리 저장소는 DB 밖에 두고 메타데이터만 DB에 저장한다.
- 삭제는 논리 삭제를 기본으로 하고 `status`로 관리한다.
- AWS 마이그레이션을 고려해 PostgreSQL 호환 타입과 제약을 사용한다.

## 핵심 엔티티

- `users`
- `files`
- `file_permissions`
- `upload_logs`

## 파일 상태 정의

- `uploaded`: 메타데이터 등록 직후 상태
- `scanning`: 바이러스 검사와 메타데이터 추출이 진행 중인 상태
- `available`: 조회와 다운로드가 가능한 상태
- `quarantined`: 검사 결과 이상이 발견되어 접근이 차단된 상태
- `deleted`: 논리 삭제된 상태

## 엔티티 관계

- `users`는 파일의 소유자가 된다.
- `files`는 업로드된 실제 파일의 메타데이터를 가진다.
- `file_permissions`는 파일별 접근 권한을 분리해서 관리한다.
- `upload_logs`는 업로드와 삭제 같은 운영 이벤트를 기록한다.

## 사용자 테이블

### `users`

- `id` BIGSERIAL, PK
- `email` VARCHAR(255), NOT NULL, UNIQUE
- `password_hash` VARCHAR(255), NOT NULL
- `name` VARCHAR(100), NOT NULL
- `role` VARCHAR(30), NOT NULL, 기본값 `USER`
- `created_at` TIMESTAMP WITH TIME ZONE, NOT NULL
- `updated_at` TIMESTAMP WITH TIME ZONE, NOT NULL

### 인덱스

- `idx_users_email` on `email`
- `idx_users_role` on `role`

## 파일 테이블

### `files`

- `id` BIGSERIAL, PK
- `owner_id` BIGINT, NOT NULL, FK -> `users.id`
- `original_file_name` VARCHAR(255), NOT NULL
- `stored_file_name` VARCHAR(255), NOT NULL
- `storage_path` VARCHAR(500), NOT NULL
- `size` BIGINT, NOT NULL
- `mime_type` VARCHAR(100), NOT NULL
- `status` VARCHAR(30), NOT NULL, 기본값 `uploaded`
- `scan_status` VARCHAR(30), NOT NULL, 기본값 `PENDING`
- `extracted_metadata` JSONB, NULL
- `description` TEXT, NULL
- `deleted_at` TIMESTAMP WITH TIME ZONE, NULL
- `scanned_at` TIMESTAMP WITH TIME ZONE, NULL
- `quarantined_at` TIMESTAMP WITH TIME ZONE, NULL
- `created_at` TIMESTAMP WITH TIME ZONE, NOT NULL
- `updated_at` TIMESTAMP WITH TIME ZONE, NOT NULL

### 인덱스

- `idx_files_owner_id` on `owner_id`
- `idx_files_status` on `status`
- `idx_files_scan_status` on `scan_status`
- `idx_files_created_at` on `created_at`
- `idx_files_deleted_at` on `deleted_at`
- `idx_files_storage_path` on `storage_path`: 업로드 결과 확인과 고아 객체 점검에서 저장 위치로 조회한다.

## 권한 테이블

### `file_permissions`

- `id` BIGSERIAL, PK
- `file_id` BIGINT, NOT NULL, FK -> `files.id`
- `user_id` BIGINT, NOT NULL, FK -> `users.id`
- `permission_type` VARCHAR(30), NOT NULL
- `created_at` TIMESTAMP WITH TIME ZONE, NOT NULL

### 인덱스

- `idx_file_permissions_file_id` on `file_id`
- `idx_file_permissions_user_id` on `user_id`
- `uq_file_permissions_file_user` on (`file_id`, `user_id`)

## 업로드 요청 테이블

### `upload_requests`

`Idempotency-Key`가 있는 업로드 요청을 기록한다. 결정 근거는 [ADR-001](../adr/001-UPLOAD-IDEMPOTENCY-KEY.md)이다.

- `id` BIGSERIAL, PK
- `owner_id` BIGINT, NOT NULL, 요청한 사용자
- `idempotency_key` VARCHAR(100), NOT NULL
- `request_hash` VARCHAR(64), NOT NULL, 파일명·크기·설명·본문 SHA-256의 SHA-256
- `status` VARCHAR(20), NOT NULL, `IN_PROGRESS` / `COMPLETED` / `FAILED`
- `storage_path` VARCHAR(500), NULL, 이 시도가 쓰는 객체 위치. 객체를 쓰기 전에 기록한다.
- `file_id` BIGINT, NULL, 완료 시 `files.id`
- `created_at`, `updated_at`, `expires_at` TIMESTAMP WITH TIME ZONE, NOT NULL
- `version` BIGINT, NOT NULL, 재시작 경합 방지용 조건부 갱신 값

### 인덱스와 제약

- `uk_upload_requests_owner_key` UNIQUE (`owner_id`, `idempotency_key`)
- `idx_upload_requests_status_updated_at` on (`status`, `updated_at`): 오래된 진행 중 요청 조회
- `idx_upload_requests_expires_at` on `expires_at`: 만료 기록 정리

### 갱신 규칙

- 완료·실패 전환은 `status = IN_PROGRESS`이고 `storage_path`가 해당 시도와 같을 때만 반영한다. 완료는 `files` 저장과 같은 트랜잭션에서 수행해 하나라도 실패하면 함께 롤백한다.
- 실패, 만료, 처리 중 판단 기준(기본 15분) 초과 기록은 `version`이 그대로일 때만 새 시도로 다시 시작한다.
- `expires_at`(기본 생성 후 24시간)이 지난 행은 주기 작업이 지운다. `files` 행은 지우지 않는다.

## 업로드 로그

### `upload_logs`

- `id` BIGSERIAL, PK
- `file_id` BIGINT, NULL, FK -> `files.id`
- `user_id` BIGINT, NOT NULL, FK -> `users.id`
- `action_type` VARCHAR(30), NOT NULL
- `status` VARCHAR(30), NOT NULL
- `message` TEXT, NULL
- `created_at` TIMESTAMP WITH TIME ZONE, NOT NULL

### 인덱스

- `idx_upload_logs_file_id` on `file_id`
- `idx_upload_logs_user_id` on `user_id`
- `idx_upload_logs_created_at` on `created_at`

## 상태값 기준

- `files.status`
  - `uploaded`
  - `scanning`
  - `available`
  - `quarantined`
  - `deleted`
- `upload_logs.action_type`
  - `UPLOAD`
  - `DELETE`
  - `DOWNLOAD`
  - `LOGIN`
  - `SCAN`
  - `METADATA_EXTRACT`
- `upload_logs.status`
  - `SUCCESS`
  - `FAIL`
- `file_permissions.permission_type`
  - `READ`
  - `WRITE`
  - `OWNER`
- `upload_requests.status`
  - `IN_PROGRESS`
  - `COMPLETED`
  - `FAILED`
- `files.scan_status`
  - `PENDING`
  - `CLEAN`
  - `INFECTED`
  - `FAILED`

## 고려사항

- 조회가 많은 필드는 Redis 캐시를 고려한다.
- 삭제는 기본적으로 논리 삭제를 사용하고, 물리 삭제는 후속 정리로 분리한다.
- Aurora 마이그레이션을 고려해 PostgreSQL 호환 SQL을 유지한다.

## 구현 상태

스키마는 별도 마이그레이션 도구 없이 Hibernate `ddl-auto=update`가 JPA Entity 기준으로 만든다(`postgres` 프로필). 기본 프로필은 인메모리 저장소를 쓴다.

| 테이블·컬럼 | 상태 |
| --- | --- |
| `users` | 구현 |
| `files` | 구현. 단 `extracted_metadata`, `scanned_at`, `quarantined_at` 컬럼과 DB 기본값(`uploaded`, `PENDING`)은 Entity에 없다. 애플리케이션이 업로드 시 `AVAILABLE`·`CLEAN`을 직접 넣는다 |
| `upload_requests` | 구현 (FILE-05) |
| `file_permissions`, `upload_logs` | 미구현. 권한 공유·감사 로그를 위한 예정 설계다 |

- 상태값은 DB에 대문자 enum 이름(`AVAILABLE`, `DELETED` 등)으로 저장되고 API 응답에서 소문자로 바뀐다.
- 외래 키 제약은 Entity에 선언되어 있지 않다. `files.owner_id`와 `users.id`의 관계는 애플리케이션이 보장한다.
- 이전 Day별 구현 기준은 [이전 문서 요약](../LEGACY.md)에 남겼다.
