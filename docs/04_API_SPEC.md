# 04. API 명세

## 공통 규칙

- 모든 응답은 JSON을 기본으로 한다.
- 인증이 필요한 API는 `Authorization: Bearer <token>` 헤더를 사용한다.
- 파일 업로드는 `multipart/form-data`로 처리한다.
- 날짜와 시간은 UTC 기반 ISO-8601 문자열을 사용한다.

## 공통 응답 형식

### 성공 응답

```json
{
  "success": true,
  "data": {},
  "message": "OK"
}
```

### 실패 응답

```json
{
  "success": false,
  "errorCode": "AUTH_INVALID_TOKEN",
  "message": "Invalid token"
}
```

## 공통 에러 코드

- `AUTH_INVALID_TOKEN`: 토큰이 없거나 유효하지 않음
- `AUTH_EXPIRED_TOKEN`: 토큰이 만료됨
- `AUTH_ACCESS_DENIED`: 권한이 부족함
- `AUTH_DUPLICATED_EMAIL`: 중복 이메일
- `AUTH_INVALID_CREDENTIALS`: 비밀번호 또는 계정 정보가 일치하지 않음
- `VALIDATION_ERROR`: 입력값 검증 실패
- `RESOURCE_NOT_FOUND`: 리소스를 찾을 수 없음
- `FILE_TOO_LARGE`: 파일 크기 초과
- `FILE_INVALID_TYPE`: 허용되지 않는 파일 형식
- `FILE_UPLOAD_FAILED`: 업로드 처리 실패
- `FILE_SCAN_FAILED`: 바이러스 검사 또는 메타데이터 추출 실패
- `FILE_QUARANTINED`: 파일이 검역 상태임
- `FILE_DELETE_FAILED`: 삭제 처리 실패
- `INTERNAL_SERVER_ERROR`: 서버 내부 오류

## Health

### `GET /api/health`

- 인증 필요 여부: 아니오
- 설명: 애플리케이션의 기본 구동 상태를 확인한다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "timestamp": "2026-06-30T00:00:00Z"
  },
  "message": "OK"
}
```

주요 에러 코드:

- `INTERNAL_SERVER_ERROR`

## Authentication

### `POST /api/auth/signup`

- 인증 필요 여부: 아니오
- 설명: 신규 사용자를 등록한다.

요청 예시:

```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "name": "홍길동"
}
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER"
  },
  "message": "User created"
}
```

주요 에러 코드:

- `AUTH_DUPLICATED_EMAIL`
- `VALIDATION_ERROR`

### `POST /api/auth/login`

- 인증 필요 여부: 아니오
- 설명: 이메일과 비밀번호로 로그인하고 토큰을 발급한다.

요청 예시:

```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": 1,
      "email": "user@example.com",
      "name": "홍길동",
      "role": "USER"
    }
  },
  "message": "Login successful"
}
```

주요 에러 코드:

- `AUTH_INVALID_CREDENTIALS`
- `VALIDATION_ERROR`

### `POST /api/auth/refresh`

- 인증 필요 여부: 아니오
- 설명: 만료된 access token을 갱신한다.
- 구현 상태: Day 3 기준 미구현. Redis 또는 별도 refresh token 저장소 도입 후 구현한다.

요청 예시:

```json
{
  "refreshToken": "refresh-token-value"
}
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "message": "Token refreshed"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`
- `AUTH_EXPIRED_TOKEN`

### `POST /api/auth/logout`

- 인증 필요 여부: 예
- 설명: 현재 토큰을 무효화한다.
- 구현 상태: Day 3 기준 미구현. Redis 기반 토큰 블랙리스트 또는 refresh token 저장소 도입 후 구현한다.

응답 예시:

```json
{
  "success": true,
  "data": null,
  "message": "Logout successful"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`

## Users

### `GET /api/users/me`

- 인증 필요 여부: 예
- 설명: 현재 로그인한 사용자의 정보를 조회한다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "createdAt": "2026-06-30T00:00:00Z",
    "updatedAt": "2026-06-30T00:00:00Z"
  },
  "message": "OK"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`

### `GET /api/users/{id}`

- 인증 필요 여부: 예
- 설명: 특정 사용자 정보를 조회한다. 기본적으로 관리자만 접근한다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "userId": 2,
    "email": "admin@example.com",
    "name": "관리자",
    "role": "ADMIN"
  },
  "message": "OK"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`
- `AUTH_ACCESS_DENIED`
- `RESOURCE_NOT_FOUND`

### `PATCH /api/users/me`

- 인증 필요 여부: 예
- 설명: 현재 로그인한 사용자의 이름 또는 비밀번호를 수정한다.

요청 예시:

```json
{
  "name": "홍길동2"
}
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "name": "홍길동2",
    "role": "USER"
  },
  "message": "Profile updated"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`
- `VALIDATION_ERROR`

## Files

### `POST /api/files`

- 인증 필요 여부: 예
- 설명: 파일을 업로드하고 메타데이터를 저장한다.
- 요청 형식: `multipart/form-data`

요청 예시:

```text
file: <binary>
```

선택 필드 예시:

```text
description: project plan
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "fileId": 100,
    "originalFileName": "design.pdf",
    "storedFileName": "a1b2c3.pdf",
    "size": 1048576,
    "mimeType": "application/pdf",
    "status": "available",
    "scanStatus": "CLEAN",
    "ownerId": 1
  },
  "message": "File uploaded"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`
- `FILE_TOO_LARGE`
- `FILE_INVALID_TYPE`
- `FILE_UPLOAD_FAILED`
- `FILE_SCAN_FAILED`
- `VALIDATION_ERROR`

### `GET /api/files`

- 인증 필요 여부: 예
- 설명: 접근 가능한 파일 목록을 조회한다.

응답 예시:

```json
{
  "success": true,
  "data": [
    {
      "fileId": 100,
      "originalFileName": "design.pdf",
      "size": 1048576,
      "mimeType": "application/pdf",
      "status": "available",
      "scanStatus": "CLEAN",
      "createdAt": "2026-06-30T00:00:00Z"
    }
  ],
  "message": "OK"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`

### `GET /api/files/{id}`

- 인증 필요 여부: 예
- 설명: 파일 메타데이터를 조회한다.
- `status`가 `scanning` 또는 `quarantined`일 수 있다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "fileId": 100,
    "originalFileName": "design.pdf",
    "storedFileName": "a1b2c3.pdf",
    "size": 1048576,
    "mimeType": "application/pdf",
    "status": "available",
    "scanStatus": "CLEAN",
    "ownerId": 1,
    "createdAt": "2026-06-30T00:00:00Z",
    "updatedAt": "2026-06-30T00:00:00Z"
  },
  "message": "OK"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`
- `AUTH_ACCESS_DENIED`
- `RESOURCE_NOT_FOUND`

### `GET /api/files/{id}/download`

- 인증 필요 여부: 예
- 설명: 파일 바이너리를 다운로드한다.
- `available` 상태의 파일만 다운로드할 수 있다.

응답 형식:

- `Content-Disposition: attachment`
- 파일 바이너리 응답

주요 에러 코드:

- `AUTH_INVALID_TOKEN`
- `AUTH_ACCESS_DENIED`
- `FILE_QUARANTINED`
- `RESOURCE_NOT_FOUND`

### `DELETE /api/files/{id}`

- 인증 필요 여부: 예
- 설명: 파일을 논리 삭제한다.
- `quarantined` 상태의 파일도 삭제는 가능하다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "fileId": 100,
    "status": "deleted"
  },
  "message": "File deleted"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`
- `AUTH_ACCESS_DENIED`
- `RESOURCE_NOT_FOUND`
- `FILE_DELETE_FAILED`

## 문서화 기준

- 요청/응답 예시를 함께 적는다.
- 인증 필요 여부를 endpoint마다 명시한다.
- 권한 실패와 검증 실패를 분리해서 정의한다.
- 파일 업로드는 `multipart/form-data` 기준으로 설명한다.
- 파일 삭제는 논리 삭제 기준으로 설명한다.

## Day 1 기준

- 인증, 사용자, 파일 API의 경계가 고정된다.
- 공통 응답과 에러 코드가 정리된다.
- API 문서가 DB와 요구사항 문서의 용어를 따른다.

## Day 3 구현 기준

- `POST /api/auth/signup`은 인메모리 사용자 저장소에 사용자를 등록한다.
- `POST /api/auth/login`은 이메일/비밀번호 검증 후 HS256 JWT access token을 발급한다.
- `GET /api/users/me`는 Bearer token의 사용자 식별자로 현재 사용자 정보를 조회한다.
- `GET /api/users/{id}`는 `ADMIN` 역할만 접근할 수 있다.
- `PATCH /api/users/me`는 현재 사용자의 이름을 수정한다.
- Day 3 기준 refresh token, logout token blacklist, 사용자 영속 저장은 후속 일정에서 구현한다.

## Day 4 구현 기준

- `POST /api/files`는 인증된 사용자의 파일을 로컬 파일 시스템에 저장하고 인메모리 메타데이터 저장소에 등록한다.
- Day 4는 바이러스 검사/비동기 후처리 전 단계이므로 업로드 직후 상태를 `available`, 검사 상태를 `CLEAN`으로 둔다.
- 허용 확장자는 `pdf`, `png`, `jpg`, `jpeg`, `txt`, `csv`이며 기본 최대 크기는 10MiB이다.
- `GET /api/files`는 현재 사용자가 소유한 삭제되지 않은 파일만 반환한다.
- `GET /api/files/{id}`와 `GET /api/files/{id}/download`는 소유자 또는 `ADMIN`만 접근할 수 있다.
- `DELETE /api/files/{id}`는 물리 파일을 즉시 삭제하지 않고 메타데이터 상태를 `deleted`로 변경한다.
- Day 5 이후 파일 메타데이터 저장소는 PostgreSQL/JPA로, 파일 바이너리 저장소는 MinIO로 교체한다.

## Day 7 온프레미스 검증 기준

- Docker Compose 실행 환경에서도 외부 API 계약은 동일하게 유지한다.
- Client는 `http://localhost:8080`의 Nginx 진입점으로만 API를 호출한다.
- `postgres,redis,minio` 프로필 활성화 시 사용자와 파일 메타데이터는 PostgreSQL에 저장된다.
- 파일 바이너리는 MinIO `files` 버킷에 object key 기준으로 저장된다.
- 파일 단건 조회와 업로드 이후 Redis에 `files:metadata:{fileId}` 형식의 캐시가 생성될 수 있다.
- 같은 파일을 중복 업로드하면 현재 구현에서는 새 파일로 취급하며, 별도의 파일명/hash 중복 제한은 없다.
- `POST /api/auth/refresh`, `POST /api/auth/logout`은 API 문서상 예정 계약이며 Day 7 온프레미스 검증 대상에서 제외한다.
- 수동 검증 절차는 `docs/11_ONPREM_MANUAL_TEST.md`를 기준으로 한다.
