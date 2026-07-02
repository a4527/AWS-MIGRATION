# 03. Database

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
- `files.scan_status`
  - `PENDING`
  - `CLEAN`
  - `INFECTED`
  - `FAILED`

## 고려사항

- 조회가 많은 필드는 Redis 캐시를 고려한다.
- 삭제는 기본적으로 논리 삭제를 사용하고, 물리 삭제는 후속 정리로 분리한다.
- Aurora 마이그레이션을 고려해 PostgreSQL 호환 SQL을 유지한다.

## Day 1 기준

- 파일 상태값이 요구사항 문서와 일치한다.
- 삭제 정책이 논리 삭제 중심으로 고정된다.
- 이후 API 문서에서 사용하는 파일 상태와 권한 개념이 동일해야 한다.
- 테이블과 컬럼 수준의 초안이 고정된다.

## Day 5 구현 기준

- `users`와 `files` 테이블은 JPA 엔티티로 구현한다.
- `postgres` 프로필을 활성화하면 `UserRepository`와 `FileMetadataRepository`의 JPA 어댑터가 사용된다.
- 기본 프로필은 로컬 개발과 테스트 편의를 위해 인메모리 도메인 저장소를 유지한다.
- 테스트 환경에서는 H2의 PostgreSQL compatibility mode를 사용해 JPA 매핑을 검증한다.
- 운영 PostgreSQL 연결 정보는 `application-postgres.yml`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 주입한다.
- Day 5 구현 테이블은 `users`, `files`이며, `file_permissions`, `upload_logs`는 후속 권한 공유와 운영 감사 로그 구현 시 JPA 엔티티로 확장한다.
