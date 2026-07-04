# 07. Troubleshooting

## 온프레미스 공통 확인 순서

장애 원인을 좁힐 때는 아래 순서로 확인한다.

```bash
docker compose ps
docker compose logs nginx
docker compose logs backend
docker compose logs postgres
docker compose logs redis
docker compose logs minio
```

API 진입점은 Nginx다.

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/actuator/health
```

## Docker Compose가 올라오지 않음

- 증상: `docker compose up --build -d` 실패 또는 일부 컨테이너가 `Exited` 상태다.
- 원인: 이미지 pull 실패, 포트 충돌, 환경변수 오타, Docker daemon 미실행일 수 있다.
- 해결 방법:
  - `docker compose ps`로 실패한 서비스를 확인한다.
  - `docker compose logs <service>`로 원인 로그를 확인한다.
  - 이미 사용 중인 포트가 있으면 `.env`에서 포트를 변경한다.
  - 설정이 깨졌는지 `docker compose config`로 확인한다.
- 재발 방지 체크리스트:
  - 실행 전 Docker Desktop 또는 Docker daemon 상태를 확인한다.
  - 로컬에서 `8080`, `5432`, `6379`, `9000`, `9001` 포트 사용 여부를 확인한다.

## Nginx는 떠 있지만 API 호출 실패

- 증상: `localhost:8080` 요청이 `502 Bad Gateway` 또는 연결 실패로 응답한다.
- 원인: backend 컨테이너가 아직 시작되지 않았거나 Spring Boot가 기동 실패했을 수 있다.
- 해결 방법:
  - `docker compose ps`에서 `backend` 상태를 확인한다.
  - `docker compose logs backend`로 Spring Boot 오류를 확인한다.
  - `docker compose logs nginx`에서 upstream 연결 오류를 확인한다.
- 재발 방지 체크리스트:
  - `backend`가 `postgres`, `redis`, `minio` 연결 정보를 올바르게 받는지 확인한다.
  - Nginx upstream이 `backend:8080`을 가리키는지 확인한다.

## PostgreSQL 연결 실패

- 증상: 회원가입, 로그인, 파일 메타데이터 저장 API가 500 오류를 반환한다.
- 원인: PostgreSQL 컨테이너가 healthy 상태가 아니거나 `DB_URL`, 계정, 비밀번호가 맞지 않을 수 있다.
- 해결 방법:
  - `docker compose ps`에서 `postgres`가 `healthy`인지 확인한다.
  - `docker compose logs postgres`로 DB 기동 로그를 확인한다.
  - `docker compose exec postgres psql -U fileshare -d fileshare`로 직접 접속한다.
- 재발 방지 체크리스트:
  - `DB_URL`은 Compose 내부 주소인 `jdbc:postgresql://postgres:5432/fileshare`를 사용한다.
  - 운영에서는 DB 비밀번호를 코드가 아니라 secret으로 주입한다.

## Redis 캐시가 보이지 않음

- 증상: `redis-cli KEYS 'files:metadata:*'` 결과가 비어 있다.
- 원인: 파일 단건 조회 전이거나 TTL 만료, Redis 프로필 비활성화, Redis 연결 실패일 수 있다.
- 해결 방법:
  - 파일 업로드 또는 `GET /api/files/{id}`를 먼저 실행한다.
  - `docker compose exec redis redis-cli TTL "files:metadata:${FILE_ID}"`로 TTL을 확인한다.
  - `SPRING_PROFILES_ACTIVE`에 `redis`가 포함되어 있는지 확인한다.
- 재발 방지 체크리스트:
  - Redis는 원본 저장소가 아니라 캐시이므로 키가 없어도 PostgreSQL 데이터를 원본으로 본다.
  - 캐시 TTL은 현재 300초다.

## MinIO 버킷 또는 파일이 보이지 않음

- 증상: MinIO 콘솔에 `files` 버킷이나 업로드 object가 없다.
- 원인: 파일 업로드가 실패했거나 `MINIO_ENDPOINT`, 계정, 버킷 이름이 맞지 않을 수 있다.
- 해결 방법:
  - 먼저 파일 업로드 API 응답이 성공인지 확인한다.
  - `docker compose logs backend`에서 `FILE_UPLOAD_FAILED` 관련 로그를 확인한다.
  - MinIO 콘솔 `http://localhost:9001`에 `minioadmin/minioadmin`으로 로그인한다.
  - PostgreSQL `files.storage_path` 값과 MinIO object key를 비교한다.
- 재발 방지 체크리스트:
  - 백엔드는 내부 주소 `http://minio:9000`으로 MinIO에 접근한다.
  - 첫 업로드 시 `MinioFileStorage`가 버킷을 자동 생성한다.

## 파일 업로드 크기 또는 확장자 오류

- 증상: 업로드가 `FILE_TOO_LARGE` 또는 `FILE_INVALID_TYPE`으로 실패한다.
- 원인: 기본 업로드 제한은 10MiB이고 허용 확장자는 `pdf,png,jpg,jpeg,txt,csv`다.
- 해결 방법:
  - 파일 크기와 확장자를 확인한다.
  - 필요하면 `.env` 또는 Compose 환경변수의 `APP_FILE_MAX_SIZE_BYTES`, `APP_FILE_ALLOWED_EXTENSIONS`를 조정한다.
  - Nginx `client_max_body_size`도 Spring 업로드 제한과 맞춘다.
- 재발 방지 체크리스트:
  - 운영 변경 시 Spring 설정과 Nginx 설정의 업로드 제한을 함께 조정한다.

## 인증 토큰 오류

- 증상: 보호 API가 `AUTH_INVALID_TOKEN` 또는 `AUTH_ACCESS_DENIED`를 반환한다.
- 원인: `Authorization` 헤더 누락, 잘못된 access token, 권한 부족일 수 있다.
- 해결 방법:
  - 로그인 응답의 `data.accessToken`을 다시 저장한다.
  - 요청 헤더가 `Authorization: Bearer <token>` 형식인지 확인한다.
  - 일반 `USER` 토큰으로 관리자 전용 API를 호출하지 않았는지 확인한다.
- 재발 방지 체크리스트:
  - 수동 테스트 시 `echo "$ACCESS_TOKEN"`으로 토큰 값이 비어 있지 않은지 먼저 확인한다.

## 기록 방식

- 증상
- 원인
- 해결 방법
- 재발 방지 체크리스트

## AWS 전환 이후 확장 예정 이슈

- EKS 배포 실패
- ALB 헬스체크 실패
- HTTPS 인증서 적용 문제
- S3 권한 오류
- Aurora/ElastiCache 보안 그룹 연결 실패
