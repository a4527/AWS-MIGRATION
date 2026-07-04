# 11. On-Prem Manual Test

Day 7 온프레미스 검증을 직접 수행하기 위한 수동 테스트 절차다.

자동화 스크립트로 한 번에 실행하지 않고, 아래 순서대로 하나씩 실행하며 응답과 저장소 상태를 확인한다.

## 1. Compose 스택 시작

```bash
docker compose up --build -d
```

상태 확인:

```bash
docker compose ps
```

확인 기준:

- `postgres`, `redis`가 `healthy` 상태다.
- `backend`, `nginx`, `minio`가 `Up` 상태다.

## 2. Health 확인

```bash
curl http://localhost:8080/api/health
```

기대 결과:

```json
{
  "success": true,
  "data": {
    "status": "UP"
  },
  "message": "OK"
}
```

Actuator health:

```bash
curl http://localhost:8080/actuator/health
```

기대 결과:

```json
{
  "status": "UP"
}
```

## 3. 회원가입

이미 같은 이메일로 가입했다면 이메일 뒤 숫자를 바꾼다.

```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"user1@example.com","password":"Password123!","name":"홍길동"}'
```

확인 기준:

- `success`가 `true`다.
- `data.email`이 요청한 이메일과 같다.
- `data.role`이 `USER`다.

## 4. 로그인 및 토큰 저장

`jq`가 있을 때:

```bash
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user1@example.com","password":"Password123!"}' \
  | jq -r '.data.accessToken')
```

토큰 확인:

```bash
echo "$ACCESS_TOKEN"
```

`jq`가 없으면 로그인 응답에서 `data.accessToken` 값을 직접 복사한다.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user1@example.com","password":"Password123!"}'
```

직접 복사한 뒤:

```bash
ACCESS_TOKEN='<login-response-access-token>'
```

## 5. 현재 사용자 조회

```bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

확인 기준:

- 현재 로그인한 사용자 정보가 반환된다.

## 6. 사용자 이름 수정

```bash
curl -X PATCH http://localhost:8080/api/users/me \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"홍길동2"}'
```

확인 기준:

- `data.name`이 `홍길동2`로 변경된다.

## 7. 파일 업로드

테스트 파일 생성:

```bash
printf 'hello file api\n' > sample.txt
```

업로드:

```bash
UPLOAD_RESPONSE=$(curl -s -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@./sample.txt" \
  -F "description=manual test file")
```

응답 확인:

```bash
echo "$UPLOAD_RESPONSE"
```

응답의 파일 ID를 저장한다.

`jq`가 있을 때:

```bash
FILE_ID=$(echo "$UPLOAD_RESPONSE" | jq -r '.data.fileId')
```

직접 저장할 때:

```bash
FILE_ID='<upload-response-file-id>'
```

확인 기준:

- `data.fileId`가 있다.
- `data.originalFileName`이 `sample.txt`다.
- `data.status`가 `available` 상태다.

## 8. 파일 목록 조회

```bash
curl http://localhost:8080/api/files \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

확인 기준:

- 업로드한 파일이 목록에 포함된다.

## 9. 파일 메타데이터 단건 조회

```bash
curl http://localhost:8080/api/files/${FILE_ID} \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

확인 기준:

- `fileId`가 `${FILE_ID}`와 같다.
- 이 요청 이후 Redis 캐시 확인이 가능하다.

## 10. 파일 다운로드

```bash
curl -L http://localhost:8080/api/files/${FILE_ID}/download \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -o downloaded-sample.txt
```

다운로드 결과 확인:

```bash
cat downloaded-sample.txt
```

기대 결과:

```text
hello file api
```

## 11. PostgreSQL 저장 확인

```bash
docker compose exec postgres psql -U fileshare -d fileshare
```

`psql` 안에서:

```sql
SELECT id, email, name, role, created_at
FROM users
ORDER BY id;

SELECT id, owner_id, original_file_name, stored_file_name, storage_path, status, scan_status, created_at
FROM files
ORDER BY id;
```

종료:

```sql
\q
```

확인 기준:

- 가입한 사용자가 `users`에 있다.
- 업로드한 파일 메타데이터가 `files`에 있다.
- `storage_path` 값이 MinIO object key로 사용된다.

## 12. Redis 캐시 확인

파일 업로드 또는 단건 조회 이후 실행한다.

```bash
docker compose exec redis redis-cli KEYS 'files:metadata:*'
```

특정 파일 캐시 확인:

```bash
docker compose exec redis redis-cli GET "files:metadata:${FILE_ID}"
docker compose exec redis redis-cli TTL "files:metadata:${FILE_ID}"
```

확인 기준:

- 캐시 키 형식은 `files:metadata:{fileId}`다.
- TTL은 남은 초 단위로 표시된다.

## 13. MinIO 저장 확인

브라우저에서 접속한다.

```text
http://localhost:9001
```

로그인:

```text
Username: minioadmin
Password: minioadmin
```

확인 기준:

- 첫 업로드 이후 `files` 버킷이 생성되어 있다.
- PostgreSQL `files.storage_path`와 같은 object key가 버킷 안에 있다.

## 14. 파일 삭제

```bash
curl -X DELETE http://localhost:8080/api/files/${FILE_ID} \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

확인 기준:

- 삭제 응답이 성공한다.
- 파일은 물리 삭제가 아니라 메타데이터 상태가 삭제 상태로 바뀐다.

삭제 후 조회:

```bash
curl http://localhost:8080/api/files/${FILE_ID} \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

기대 결과:

- `RESOURCE_NOT_FOUND`가 반환된다.

## 15. 실패 케이스 확인

### 인증 없이 보호 API 호출

```bash
curl http://localhost:8080/api/users/me
```

기대 결과:

- `AUTH_INVALID_TOKEN`

### 허용되지 않는 확장자 업로드

```bash
printf 'not allowed\n' > sample.exe

curl -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@./sample.exe"
```

기대 결과:

- `FILE_INVALID_TYPE`

### 일반 사용자로 특정 사용자 조회

```bash
curl http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

기대 결과:

- 일반 `USER` 토큰이면 `AUTH_ACCESS_DENIED`

## 16. 스택 중지

컨테이너와 네트워크만 내린다. 볼륨 데이터는 유지된다.

```bash
docker compose down
```

데이터까지 초기화할 때만 사용한다.

```bash
docker compose down -v
```

## 17. 장애 시나리오 수동 점검

장애 시나리오는 정상 기능 검증이 끝난 뒤 선택적으로 수행한다. 각 시나리오 후에는 서비스를 다시 시작한다.

### Backend 중지

```bash
docker compose stop backend
curl -i http://localhost:8080/api/health
docker compose start backend
```

확인 기준:

- Nginx는 떠 있지만 백엔드 upstream 연결 실패가 발생한다.
- `docker compose logs nginx`에서 upstream 오류를 확인할 수 있다.

### PostgreSQL 중지

```bash
docker compose stop postgres
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user1@example.com","password":"Password123!"}'
docker compose start postgres
```

확인 기준:

- DB가 필요한 API가 실패한다.
- `docker compose logs backend`에서 DB 연결 오류를 확인할 수 있다.

### Redis 중지

```bash
docker compose stop redis
curl -i http://localhost:8080/api/files/${FILE_ID} \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
docker compose start redis
```

확인 기준:

- 현재 구현에서는 Redis 캐시 접근 실패가 API 오류로 이어질 수 있다.
- 운영 개선 시 Redis 장애를 원본 DB 조회로 우회하는 보완이 필요하다.

### MinIO 중지

```bash
docker compose stop minio
curl -i -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@./sample.txt"
docker compose start minio
```

확인 기준:

- 업로드 또는 다운로드가 실패한다.
- 응답은 `FILE_UPLOAD_FAILED` 또는 서버 오류로 확인한다.
