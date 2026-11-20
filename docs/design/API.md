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
- `IDEMPOTENCY_IN_PROGRESS`: 같은 `Idempotency-Key`의 요청이 처리 중임 (409, `Retry-After` 헤더 포함)
- `IDEMPOTENCY_KEY_MISMATCH`: 같은 `Idempotency-Key`가 다른 요청에 이미 쓰임 (422)
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
- 구현 상태: **미구현(예정 계약)**. 현재 `SecurityConfig`가 경로만 허용하며 컨트롤러가 없어 호출 시 오류가 난다. refresh token 저장소 도입 후 구현한다.

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
- 구현 상태: **미구현(예정 계약)**. 토큰 블랙리스트 또는 refresh token 저장소 도입 후 구현한다.

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

선택 헤더:

- `Idempotency-Key`: 1~100자의 영문·숫자·`.`·`_`·`:`·`-`. 요청마다 새 값(예: UUID)을 만들고, 응답을 받지 못해 다시 보낼 때는 같은 값을 쓴다. 키는 사용자별로 구분하며 24시간 보관한다. 근거는 [ADR-001](../adr/001-UPLOAD-IDEMPOTENCY-KEY.md)이다.

| 같은 키로 다시 보냈을 때 | 응답 |
| --- | --- |
| 이전 요청이 완료됨, 같은 요청 | `200`, 첫 응답과 같은 `data`, `Idempotent-Replayed: true` 헤더 |
| 이전 요청이 처리 중 | `409 IDEMPOTENCY_IN_PROGRESS`, `Retry-After: 5` |
| 다른 요청 (파일명·크기·설명·내용 중 하나라도 다름) | `422 IDEMPOTENCY_KEY_MISMATCH` |
| 이전 요청이 실패로 끝남, 같은 요청 | 새로 처리 |
| 보관 기간(24시간)이 지남 | 새 키로 취급 |

처리 중 판단 기준(기본 15분)보다 오래 끝나지 않은 요청은 중단된 것으로 보고 같은 키의 새 요청이 이어받는다.

실패 처리: 객체 저장 후 메타데이터 기록이 실패하면 짧게 재시도한다. 끝내 실패하면 `FILE_UPLOAD_FAILED`를 반환하고, 기록되지 않은 것이 확인된 경우에만 저장한 객체를 지운다. 기록 여부를 확인할 수 없으면 객체를 남기고 [관리자 저장소 점검](#관리자-저장소-점검)으로 정리한다.

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
- `IDEMPOTENCY_IN_PROGRESS`
- `IDEMPOTENCY_KEY_MISMATCH`

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
- 상태는 캐시가 아닌 DB에서 확인한다. 삭제 직후에도 다운로드되지 않는다.

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
- 캐시 반영이 실패해도 DB 반영이 끝났으면 성공으로 응답한다. 캐시는 백그라운드에서 다시 반영하며, 반영되기 전까지 최대 5분(캐시 TTL) 동안 메타데이터 조회에 삭제 전 정보가 보일 수 있다.

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

## 관리자 저장소 점검

저장소 객체 중 어떤 `files` 행(논리 삭제 포함)도 가리키지 않는 고아 객체를 찾는다. `ADMIN` 역할만 호출할 수 있고, 다른 사용자는 `403 AUTH_ACCESS_DENIED`를 받는다. `minAgeMinutes`(기본 60)보다 오래된 객체만 대상으로 하며, 진행 중인 업로드를 보호하기 위해 처리 중 판단 기준(기본 15분)보다 작으면 `400 VALIDATION_ERROR`다.

### `GET /api/admin/storage/orphans`

- 인증 필요 여부: 예 (`ADMIN`)
- 설명: 고아 객체와 처리 중 판단 기준을 넘긴 업로드 요청을 보고한다. 아무것도 지우지 않는다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "checkedAt": "2026-11-17T12:00:00Z",
    "minAgeMinutes": 60,
    "orphanObjects": [
      { "storagePath": "files/0b6c....txt", "size": 5, "lastModified": "2026-11-17T09:00:00Z" }
    ],
    "staleUploadRequests": [
      { "uploadRequestId": 3, "ownerId": 1, "storagePath": "files/0b6c....txt", "updatedAt": "2026-11-17T09:00:00Z" }
    ]
  },
  "message": "OK"
}
```

### `POST /api/admin/storage/orphans/cleanup`

- 인증 필요 여부: 예 (`ADMIN`)
- 설명: 고아 객체를 지운다. 객체마다 지우기 직전에 메타데이터가 생겼는지 다시 확인한다.

응답 예시:

```json
{
  "success": true,
  "data": { "deletedObjects": ["files/0b6c....txt"], "failedObjects": [] },
  "message": "Orphan cleanup finished"
}
```

주요 에러 코드:

- `AUTH_INVALID_TOKEN`
- `AUTH_ACCESS_DENIED`
- `VALIDATION_ERROR`
- `INTERNAL_SERVER_ERROR`: 저장소 목록 조회 실패

## 문서화 기준

- 요청/응답 예시를 함께 적는다.
- 인증 필요 여부를 endpoint마다 명시한다.
- 권한 실패와 검증 실패를 분리해서 정의한다.
- 파일 업로드는 `multipart/form-data` 기준으로 설명한다.
- 파일 삭제는 논리 삭제 기준으로 설명한다.

## 현재 구현 요약

| 엔드포인트 | 상태 | 비고 |
| --- | --- | --- |
| `GET /api/health`, `GET /actuator/health` | 구현 | 인증 없음 |
| `POST /api/auth/signup`, `POST /api/auth/login` | 구현 | 가입 역할은 항상 `USER`, HS256 access token |
| `POST /api/auth/refresh`, `POST /api/auth/logout` | 미구현 | 예정 계약 |
| `GET /api/users/me`, `PATCH /api/users/me` | 구현 | 이름만 수정 |
| `GET /api/users/{id}` | 구현 | `ADMIN`만 |
| `POST /api/files` | 구현 | 허용 확장자 `pdf`·`png`·`jpg`·`jpeg`·`txt`·`csv`, 최대 10MiB, 선택 `Idempotency-Key` |
| `GET /api/files` | 구현 | 본인 소유, 삭제 제외 |
| `GET /api/files/{id}`, `GET /api/files/{id}/download` | 구현 | 소유자 또는 `ADMIN` |
| `DELETE /api/files/{id}` | 구현 | 논리 삭제 |
| `GET /api/admin/storage/orphans`, `POST /api/admin/storage/orphans/cleanup` | 구현 | `ADMIN`만 |

- 업로드 직후 상태는 `available`·`CLEAN`이다. 검사 결과를 DB에 반영하는 흐름은 FILE-03 범위다.
- 같은 파일을 `Idempotency-Key` 없이 다시 올리면 새 파일로 저장된다.
- 10MiB를 넘는 업로드는 온프레미스에서 nginx가 `413`으로, 애플리케이션에서는 `FILE_TOO_LARGE`(400)로 거절한다.
- 상태값은 응답에서 소문자(`available`, `deleted` 등)로, 검사 상태는 대문자(`CLEAN` 등)로 표기한다.
- 검증 시나리오는 [VALIDATION](VALIDATION.md)을 따른다. 이전 Day별 구현 기준은 [이전 문서 요약](../LEGACY.md)에 남겼다.
