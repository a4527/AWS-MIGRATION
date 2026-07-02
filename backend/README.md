# Backend

Spring Boot 기반 파일 공유 API 구현 영역입니다.

## 기술 기준

- Java 17
- Gradle 8.x
- Spring Boot 3.3.x
- Spring Web, Validation, Security, Actuator
- Spring Data JPA, PostgreSQL, Redis, MinIO

## 패키지 구조

- `auth/`
- `user/`
- `file/`
- `common/`
- `config/`
- `infra/`

## 현재 구현 범위

- 애플리케이션 진입점
- 공통 응답 포맷
- 공통 에러 코드
- 전역 예외 처리
- Stateless Spring Security 설정
- JWT 발급/검증
- 회원가입과 로그인
- 현재 사용자 조회와 프로필 이름 수정
- 관리자 전용 사용자 조회 접근 제어
- 기본 개발 모드에서 사용하는 인메모리 사용자 저장소
- `postgres` 프로필에서 사용하는 JPA 사용자 저장소
- 파일 업로드, 목록 조회, 메타데이터 조회, 다운로드, 논리 삭제
- 기본 개발 모드에서 사용하는 인메모리 파일 메타데이터 저장소
- `postgres` 프로필에서 사용하는 JPA 파일 메타데이터 저장소
- 기본 개발 모드에서 사용하는 로컬 파일 저장소
- `minio` 프로필에서 사용하는 MinIO 파일 저장소
- `redis` 프로필에서 사용하는 파일 단건 메타데이터 캐시
- 파일 확장자와 크기 검증
- `GET /api/health`

## 로컬 실행

저장소 로컬 Gradle 실행 스크립트를 사용한다.

```bash
cd backend
./gradlew bootRun
```

기본 실행은 H2 datasource를 부트스트랩하지만, 도메인 저장소는 인메모리 구현을 사용하고 파일은 로컬 디스크에 저장한다.

PostgreSQL, Redis, MinIO를 사용하는 실행 예시:

먼저 외부 저장소를 로컬 컨테이너로 실행한다. Day 6 전에는 `docker-compose.yml`이 없으므로 각 서버를 개별 컨테이너로 띄운다.

```bash
docker run --name fileshare-postgres \
  -e POSTGRES_DB=fileshare \
  -e POSTGRES_USER=fileshare \
  -e POSTGRES_PASSWORD=fileshare \
  -p 5432:5432 \
  -d postgres:16

docker run --name fileshare-redis \
  -p 6379:6379 \
  -d redis:7

docker run --name fileshare-minio \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -p 9000:9000 \
  -p 9001:9001 \
  -d quay.io/minio/minio server /data --console-address ":9001"
```

이미 같은 이름의 컨테이너가 있으면 다시 시작한다.

```bash
docker start fileshare-postgres fileshare-redis fileshare-minio
```

백엔드를 외부 저장소 프로필로 실행한다.

```bash
cd backend

export DB_URL='jdbc:postgresql://localhost:5432/fileshare'
export DB_USERNAME='fileshare'
export DB_PASSWORD='fileshare'
export REDIS_HOST='localhost'
export REDIS_PORT='6379'
export MINIO_ENDPOINT='http://localhost:9000'
export MINIO_ACCESS_KEY='minioadmin'
export MINIO_SECRET_KEY='minioadmin'
export MINIO_BUCKET='files'

SPRING_PROFILES_ACTIVE=postgres,redis,minio ./gradlew bootRun
```

프로필별 역할:

- `postgres`: 사용자와 파일 메타데이터를 JPA/PostgreSQL에 저장한다.
- `redis`: 파일 단건 메타데이터를 `files:metadata:{fileId}` 키로 캐시한다.
- `minio`: 실제 파일 바이너리를 MinIO 버킷에 저장한다.

### PostgreSQL, Redis, MinIO 데이터 확인

API로 회원가입, 로그인, 파일 업로드를 수행한 뒤 각 저장소에서 실제 데이터를 확인할 수 있다.

PostgreSQL에는 사용자 정보와 파일 메타데이터가 저장된다.

```bash
docker exec -it fileshare-postgres psql -U fileshare -d fileshare
```

`psql` 접속 후 테이블과 데이터를 조회한다.

```sql
\dt

SELECT id, email, name, role, created_at
FROM users
ORDER BY id;

SELECT id,
       owner_id,
       original_file_name,
       stored_file_name,
       storage_path,
       size,
       status,
       scan_status,
       created_at
FROM files
ORDER BY id;
```

Redis에는 파일 단건 조회용 메타데이터 캐시가 저장된다. 업로드 또는 단건 조회 후 확인한다.

```bash
docker exec -it fileshare-redis redis-cli KEYS 'files:metadata:*'
docker exec -it fileshare-redis redis-cli GET "files:metadata:${FILE_ID}"
docker exec -it fileshare-redis redis-cli TTL "files:metadata:${FILE_ID}"
```

캐시 키는 `files:metadata:{fileId}` 형식이고 TTL은 300초다. 캐시는 조회 성능을 위한 보조 저장소이므로 키가 없더라도 PostgreSQL의 `files` 테이블 데이터가 원본이다.

MinIO에는 실제 파일 바이너리가 저장된다. 브라우저에서 MinIO 콘솔에 접속한다.

```text
http://localhost:9001
```

로그인 정보:

```text
Username: minioadmin
Password: minioadmin
```

`files` 버킷 안에서 PostgreSQL `files.storage_path` 값과 같은 object key를 찾으면 업로드된 실제 파일을 확인할 수 있다.

## 테스트

```bash
cd backend
./gradlew test
```

## API 확인

애플리케이션 실행 후 아래 명령을 사용한다.

상태 확인:

```bash
curl http://localhost:8080/api/health
```

회원가입:

```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"Password123!","name":"홍길동"}'
```

로그인:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"Password123!"}'
```

로그인 응답의 `data.accessToken` 값을 환경변수로 저장한다.

```bash
ACCESS_TOKEN='<login-response-access-token>'
```

`jq`가 설치되어 있다면 로그인과 토큰 저장을 한 번에 처리할 수 있다.

```bash
ACCESS_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"Password123!"}' \
  | jq -r '.data.accessToken')
```

현재 로그인 사용자 조회:

```bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

현재 로그인 사용자 이름 수정:

```bash
curl -X PATCH http://localhost:8080/api/users/me \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"홍길동2"}'
```

특정 사용자 조회:

```bash
curl http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Day 3 기준 `GET /api/users/{id}`는 `ADMIN` 역할만 접근할 수 있다. 일반 `USER` 토큰으로 요청하면 `AUTH_ACCESS_DENIED`가 반환된다.

### 파일 업로드, 다운로드, 삭제 확인

기본 개발 모드에서는 파일 메타데이터가 서버 메모리에 저장되고, 실제 파일은 로컬 디스크에 저장된다. `postgres,minio` 프로필을 사용하면 메타데이터는 PostgreSQL에, 실제 파일은 MinIO에 저장된다.

기본 저장 위치:

```text
backend/build/local-storage
```

기본 개발 모드에서는 서버를 재시작하면 메모리에 있던 메타데이터는 사라진다. 로컬 파일이 디스크에 남아 있어도 API로는 조회할 수 없으므로, 업로드부터 다운로드까지 같은 실행 세션에서 확인한다. `postgres` 프로필을 사용하면 사용자 정보와 파일 메타데이터는 재시작 후에도 DB에 남는다.

예시 파일을 만든다.

```bash
printf 'hello file api\n' > sample.txt
```

파일을 업로드한다.

```bash
curl -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@./sample.txt" \
  -F "description=sample file"
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "fileId": 100,
    "originalFileName": "sample.txt",
    "storedFileName": "5f2f7e0a-8d3b-48df-9e8f-04af2d71d8f4.txt",
    "size": 15,
    "mimeType": "text/plain",
    "status": "available",
    "scanStatus": "CLEAN",
    "ownerId": 1,
    "description": "sample file",
    "createdAt": "2026-07-02T00:00:00Z",
    "updatedAt": "2026-07-02T00:00:00Z"
  },
  "message": "File uploaded"
}
```

`jq`가 설치되어 있다면 업로드하면서 `fileId`를 환경변수에 저장할 수 있다.

```bash
FILE_ID=$(curl -s -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@./sample.txt" \
  -F "description=sample file" \
  | jq -r '.data.fileId')
```

파일 목록을 조회한다.

```bash
curl http://localhost:8080/api/files \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

파일 메타데이터를 단건 조회한다.

```bash
curl http://localhost:8080/api/files/${FILE_ID} \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

파일을 다운로드한다.

```bash
curl -L http://localhost:8080/api/files/${FILE_ID}/download \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -o downloaded-sample.txt
```

다운로드한 파일 내용을 확인한다.

```bash
cat downloaded-sample.txt
```

예상 출력:

```text
hello file api
```

파일을 삭제한다. Day 4 기준 삭제는 실제 로컬 파일을 지우는 것이 아니라 메타데이터 상태를 `deleted`로 바꾸는 논리 삭제다.

```bash
curl -X DELETE http://localhost:8080/api/files/${FILE_ID} \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

삭제 후 같은 파일을 조회하면 `RESOURCE_NOT_FOUND`가 반환된다.

```bash
curl http://localhost:8080/api/files/${FILE_ID} \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

응답 예시:

```json
{
  "success": false,
  "message": "Resource not found",
  "errorCode": "RESOURCE_NOT_FOUND"
}
```

허용되지 않는 확장자를 업로드하면 `FILE_INVALID_TYPE`이 반환된다.

```bash
printf 'not allowed\n' > sample.exe

curl -X POST http://localhost:8080/api/files \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -F "file=@./sample.exe"
```

다른 일반 사용자로 로그인하면 이 파일의 메타데이터 조회와 다운로드는 `AUTH_ACCESS_DENIED`로 차단된다. 사용자별 로컬 폴더는 만들지 않고, 소유자 구분은 메타데이터의 `ownerId`로 처리한다.

인증 없이 보호 API 호출:

```bash
curl http://localhost:8080/api/users/me
```

Day 5 기준 refresh token 발급, logout 토큰 무효화, 공유 권한 테이블, 업로드 로그 테이블은 아직 구현 전이다.
