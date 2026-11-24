현재 코드 기준의 검증 시나리오와 판정 기준이다. 실행 결과는 수행일의 작업일지에 시나리오 ID와 함께 기록하며, 이 문서의 작성은 검증 성공을 뜻하지 않는다. 이전 Day 7·13 절차는 [이전 문서 요약](../LEGACY.md#이전-검증-절차-요약)에 남겼다.

## 시나리오 구성

| 묶음 | 내용 | 실행 환경 | 관련 작업 |
| --- | --- | --- | --- |
| V-ONP | 스택 기동 확인 | 온프레미스 | ONP-01 |
| V-FUNC | 인증·사용자·파일 API 기능 | 온프레미스, AWS | ONP-02, VERIFY-01 |
| V-DATA | DB 행·객체 키·캐시 키 계약 | 온프레미스, AWS | ONP-02, VERIFY-01 |
| V-IDEM | `Idempotency-Key` 동작 | 온프레미스, AWS | ONP-03, FILE-05 |
| V-FAIL | Redis·PostgreSQL·MinIO·backend 장애 주입 | 온프레미스 | ONP-03, FILE-01/02 |
| V-ADMIN | 관리자 고아 객체 점검 | 온프레미스, AWS | ONP-03, FILE-01 |
| V-LOC | AWS 전용 경로(S3 SDK·컨테이너 한도·DB 재개 지연·Lambda·CD 태그) 사전 확인 | 로컬 | PORT-01, VERIFY-01 선행 |
| V-MIG | 데이터 이전 전후 비교 | AWS | MIG-02 |
| V-AWS | AWS 진입점·후처리·장애 복구·알람 | AWS | VERIFY-01/02 |
| V-PERF | smoke 성능·자원 사용 | 온프레미스, AWS | PERF-01, FILE-04 |

각 시나리오는 독립적으로 다시 실행할 수 있도록 매번 새 이메일·키를 쓴다. 자동 테스트(`./gradlew test`)로 확인한 범위는 [작업일지](../worklogs/2026-11-17.md#file-01020405-구현)를 따르며, 아래 시나리오는 실제 서버에서 같은 동작을 확인하는 용도다.

## 0. 공통 준비

필요 도구: `curl`, `jq`, `sha256sum`, Docker Compose. 저장소 루트에서 실행한다.

```bash
cd ~/AWS-MIGRATION            # 저장소 루트
set -a; [ -f .env ] && . ./.env; set +a

BASE_URL=${BASE_URL:-http://localhost:8080}
RUN_ID=$(date +%s)
PASSWORD='Password123!'

signup() {  # signup <email> <name>
  curl -s -X POST "$BASE_URL/api/auth/signup" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\",\"name\":\"$2\"}"
}
login() {   # login <email>  → access token 출력
  curl -s -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" | jq -r '.data.accessToken'
}
api() {     # api <token> <curl 인자...>
  local token=$1; shift
  curl -s -H "Authorization: Bearer $token" "$@"
}
psql_q() {  # 온프레미스 PostgreSQL 조회
  docker compose exec -T postgres psql -U "${POSTGRES_USER:-fileshare}" -d "${POSTGRES_DB:-fileshare}" -At -c "$1"
}
mc_cmd() {  # 온프레미스 MinIO 클라이언트
  docker compose --profile tools run --rm mc "$1"
}
redis_cmd() {
  docker compose exec -T redis redis-cli "$@"
}
```

응답 형식: 성공은 `{"success":true,"data":...,"message":...}`, 실패는 `{"success":false,"errorCode":...,"message":...}`다. HTTP 상태 코드를 함께 보려면 `curl -s -o /dev/stderr -w '%{http_code}\n'`을 쓴다.

## 1. 온프레미스 기동

### V-ONP-01 스택 기동

[온프레미스 구축](DEPLOYMENT.md#3-온프레미스-구축)으로 스택을 띄운 뒤 확인한다.

```bash
docker compose ps
curl -s "$BASE_URL/api/health" | jq
curl -s "$BASE_URL/actuator/health" | jq
psql_q "select table_name from information_schema.tables where table_schema='public' order by 1;"
docker volume ls | grep -E 'postgres-data|redis-data|minio-data'
```

판정:

- `postgres`, `redis`는 `healthy`, `minio`·`backend`·`nginx`는 `running`이다.
- 두 health 응답이 `UP`이다.
- 테이블 목록에 `users`, `files`, `upload_requests`가 있다.
- 볼륨 3개가 있다.

## 2. 기능 시나리오

같은 명령을 AWS에서도 `BASE_URL`만 바꿔 실행한다.

### V-FUNC-01 회원가입·로그인·사용자 조회

```bash
USER_EMAIL="user-$RUN_ID@example.com"
signup "$USER_EMAIL" "검증 사용자" | jq
TOKEN=$(login "$USER_EMAIL"); echo "${TOKEN:0:20}..."
api "$TOKEN" "$BASE_URL/api/users/me" | jq
api "$TOKEN" -X PATCH "$BASE_URL/api/users/me" -H 'Content-Type: application/json' -d '{"name":"검증 사용자2"}' | jq '.data.name'
signup "$USER_EMAIL" "중복" | jq '.errorCode'
```

판정: 가입 응답의 `role`이 `USER`, 로그인 토큰이 있음, `me`가 같은 이메일, 이름 변경 반영, 중복 가입은 `AUTH_DUPLICATED_EMAIL`(409).

### V-FUNC-02 업로드·목록·단건·다운로드

```bash
printf 'validation %s\n' "$RUN_ID" > "sample-$RUN_ID.txt"
UPLOAD=$(api "$TOKEN" -X POST "$BASE_URL/api/files" -F "file=@sample-$RUN_ID.txt" -F "description=V-FUNC-02")
echo "$UPLOAD" | jq
FILE_ID=$(echo "$UPLOAD" | jq -r '.data.fileId')

api "$TOKEN" "$BASE_URL/api/files" | jq '.data.items | length'
api "$TOKEN" "$BASE_URL/api/files/$FILE_ID" | jq '.data | {fileId,status,scanStatus,storedFileName}'
api "$TOKEN" "$BASE_URL/api/files/$FILE_ID/download" -o "down-$RUN_ID.txt"
sha256sum "sample-$RUN_ID.txt" "down-$RUN_ID.txt"
```

판정: 업로드 응답 `status`가 `available`, `scanStatus`가 `CLEAN`, 목록에 포함, 두 파일의 해시가 같다.

### V-FUNC-03 권한과 입력 검증

```bash
OTHER_EMAIL="other-$RUN_ID@example.com"; signup "$OTHER_EMAIL" "다른 사용자" > /dev/null
OTHER_TOKEN=$(login "$OTHER_EMAIL")

api "$OTHER_TOKEN" "$BASE_URL/api/files/$FILE_ID" | jq '.errorCode'                 # 남의 파일
curl -s "$BASE_URL/api/files" | jq '.errorCode'                                      # 토큰 없음
api "$TOKEN" "$BASE_URL/api/users/1" | jq '.errorCode'                               # 관리자 전용
printf 'x' > "bad-$RUN_ID.exe"
api "$TOKEN" -X POST "$BASE_URL/api/files" -F "file=@bad-$RUN_ID.exe" | jq '.errorCode'
head -c 10485761 /dev/zero > "big-$RUN_ID.txt"
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" -X POST "$BASE_URL/api/files" -F "file=@big-$RUN_ID.txt"
```

판정: `AUTH_ACCESS_DENIED`(403), `AUTH_INVALID_TOKEN`(401), `AUTH_ACCESS_DENIED`(403), `FILE_INVALID_TYPE`(400). 10MB 초과 파일은 nginx가 먼저 `413`으로 막는다(`client_max_body_size 10m`). AWS(ALB)에서는 애플리케이션까지 도달해 `FILE_TOO_LARGE`(400) 또는 Spring의 multipart 한도 오류가 날 수 있으므로 실제 코드를 기록한다.

### V-FUNC-04 삭제

```bash
api "$TOKEN" -X DELETE "$BASE_URL/api/files/$FILE_ID" | jq '.data'
api "$TOKEN" "$BASE_URL/api/files/$FILE_ID" | jq '.errorCode'
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/files/$FILE_ID/download"
api "$TOKEN" "$BASE_URL/api/files?size=100" | jq "[.data.items[] | select(.fileId==$FILE_ID)] | length"
```

판정: 삭제 응답 `status`가 `deleted`, 이후 조회 `RESOURCE_NOT_FOUND`, 다운로드 `404`, 목록에서 제외.

## 3. 저장소 계약

### V-DATA-01 DB 행과 객체

V-FUNC-02 직후(삭제 전 또는 새 파일로) 실행한다.

```bash
psql_q "select id, owner_id, stored_file_name, storage_path, status, scan_status from files where id=$FILE_ID;"
STORAGE_PATH=$(psql_q "select storage_path from files where id=$FILE_ID;")
mc_cmd "mc stat local/${MINIO_BUCKET:-files}/$STORAGE_PATH"
mc_cmd "mc cat local/${MINIO_BUCKET:-files}/$STORAGE_PATH" | sha256sum
```

판정: `storage_path`는 `<uuid>.<확장자>`이고 같은 키의 객체가 버킷에 있으며 해시가 원본과 같다. 삭제한 파일도 행(`status=DELETED`)과 객체가 남는다(논리 삭제).

### V-DATA-02 캐시 키와 버전

```bash
api "$TOKEN" "$BASE_URL/api/files/$FILE_ID" > /dev/null
redis_cmd GET "files:metadata:$FILE_ID" | jq '{id,status,version}'
redis_cmd PTTL "files:metadata:$FILE_ID"
```

판정: 키 형식 `files:metadata:{fileId}`, 값에 `version`(updatedAt 마이크로초) 포함, TTL 300초 이하. 삭제 후에는 키가 사라지지 않고 `status`가 `DELETED`, `version`이 더 커진다.

## 4. 멱등성

### V-IDEM-01 같은 키 재전송

```bash
KEY="idem-$RUN_ID"
printf 'idem %s\n' "$RUN_ID" > "idem-$RUN_ID.txt"
for i in 1 2; do
  curl -s -D "h$i.txt" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
    -X POST "$BASE_URL/api/files" -F "file=@idem-$RUN_ID.txt" | jq -c '.data.fileId'
  grep -i idempotent-replayed "h$i.txt" || echo "(no replay header)"
done
psql_q "select status, file_id, storage_path from upload_requests where idempotency_key='$KEY';"
```

판정: 두 응답의 `fileId`가 같고, 두 번째에만 `Idempotent-Replayed: true`, `upload_requests` 행이 `COMPLETED`, 파일 행·객체는 하나다.

### V-IDEM-02 같은 키, 다른 요청

```bash
printf 'changed\n' > "idem2-$RUN_ID.txt"
api "$TOKEN" -H "Idempotency-Key: $KEY" -X POST "$BASE_URL/api/files" -F "file=@idem2-$RUN_ID.txt" | jq '.errorCode'
api "$TOKEN" -H "Idempotency-Key: bad key" -X POST "$BASE_URL/api/files" -F "file=@idem-$RUN_ID.txt" | jq '.errorCode'
api "$OTHER_TOKEN" -H "Idempotency-Key: $KEY" -X POST "$BASE_URL/api/files" -F "file=@idem-$RUN_ID.txt" | jq '.data.fileId'
```

판정: `IDEMPOTENCY_KEY_MISMATCH`(422), `VALIDATION_ERROR`(400), 다른 사용자는 같은 키로 새 파일이 생긴다(키는 사용자별).

### V-IDEM-03 동시 요청

```bash
head -c 9000000 /dev/urandom > "conc-$RUN_ID.txt"
KEY2="conc-$RUN_ID"
for i in 1 2; do
  curl -s -o "conc$i.json" -D "conc$i.h" -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY2" \
    -X POST "$BASE_URL/api/files" -F "file=@conc-$RUN_ID.txt" &
done; wait
head -1 conc1.h conc2.h; grep -i retry-after conc1.h conc2.h; jq -c '{fileId: .data.fileId, errorCode}' conc1.json conc2.json
```

판정: 하나는 `200`, 다른 하나는 `409 IDEMPOTENCY_IN_PROGRESS`와 `Retry-After: 5` 또는 `200` 재전송(먼저 끝난 경우)이다. 어느 경우에도 파일 행은 하나다. 로컬에서 경합이 재현되지 않으면 결과를 그대로 기록한다(자동 테스트로 보완됨).

## 5. 장애 주입

각 시나리오 후 `docker compose ps`로 모든 서비스가 다시 정상인지 확인한다. 로그는 `docker compose logs backend --since 5m | grep -E 'Cache|Upload|Compensating'`로 본다.

### V-FAIL-01 Redis 중지 중 조회·다운로드·삭제

```bash
NEW=$(api "$TOKEN" -X POST "$BASE_URL/api/files" -F "file=@sample-$RUN_ID.txt" | jq -r '.data.fileId')
docker compose stop redis
time api "$TOKEN" "$BASE_URL/api/files/$NEW" | jq '.data.status'
api "$TOKEN" "$BASE_URL/api/files/$NEW/download" | sha256sum
time api "$TOKEN" -X DELETE "$BASE_URL/api/files/$NEW" | jq '.data.status'
docker compose start redis
```

판정: 조회·다운로드·삭제가 모두 성공한다. 조회는 약 1초, 삭제는 약 2.5초 이내에 끝난다(Redis 명령 타임아웃 500ms × 캐시 호출 수, 삭제는 즉시 재시도 3회 포함). 2026-11-17 실행에서 조회 1.1초, 삭제 2.2초였다. 로그에 `Cache read failed; falling back to database`, `retrying in background`가 남는다.

### V-FAIL-02 Redis 일시 중지 중 삭제한 파일이 다시 보이지 않음

삭제 전에 캐시에 `available` 값이 있는 상태를 만든 뒤, Redis를 잠깐 멈춘 사이 삭제한다.

```bash
NEW=$(api "$TOKEN" -X POST "$BASE_URL/api/files" -F "file=@sample-$RUN_ID.txt" | jq -r '.data.fileId')
api "$TOKEN" "$BASE_URL/api/files/$NEW" > /dev/null          # 캐시 채우기
docker compose stop redis
api "$TOKEN" -X DELETE "$BASE_URL/api/files/$NEW" | jq '.data.status'
sleep 20; docker compose start redis
sleep 70                                                        # 백그라운드 재시도(1·2·4·…초) 대기
redis_cmd GET "files:metadata:$NEW" | jq '{status,version}'
api "$TOKEN" "$BASE_URL/api/files/$NEW" | jq '.errorCode'
docker compose logs backend --since 3m | grep -E 'Cache update succeeded in background|failed permanently'
```

판정: 캐시 값이 `DELETED`로 바뀌고 조회가 `RESOURCE_NOT_FOUND`다. 로그에 `Cache update succeeded in background`가 있다. Redis를 2분 넘게 멈추면 재시도가 끝나 `failed permanently`가 남고, 재시작 후 최대 5분(TTL) 동안 메타데이터 조회에 이전 값이 보일 수 있다. 이 경우에도 다운로드는 `404`여야 한다([DEC-003](../DECISIONS.md#결정-목록)의 한계).

### V-FAIL-03 PostgreSQL 중지 중 업로드

```bash
docker compose stop postgres
curl -s -o /dev/null -w '%{http_code} %{time_total}s\n' -H "Authorization: Bearer $TOKEN" \
  -X POST "$BASE_URL/api/files" -F "file=@sample-$RUN_ID.txt"
docker compose logs backend --since 5m | grep -E 'File metadata save failed|Upload outcome unknown'
docker compose start postgres
```

판정: 응답이 실패다. DB 연결 대기(Hikari 기본 30초)×재시도 때문에 nginx가 60초에 `504`를 먼저 돌려줄 수 있으며, backend는 계속 처리해 로그에 `Upload outcome unknown; object kept for reconciliation`을 남긴다. 결과 불명이므로 MinIO에 객체가 남는 것이 정상이며 V-ADMIN-02에서 정리한다.

### V-FAIL-04 MinIO 중지 중 업로드·다운로드

```bash
OLD=$(api "$TOKEN" -X POST "$BASE_URL/api/files" -F "file=@sample-$RUN_ID.txt" | jq -r '.data.fileId')
BEFORE=$(psql_q "select count(*) from files;")
docker compose stop minio
api "$TOKEN" -X POST "$BASE_URL/api/files" -F "file=@sample-$RUN_ID.txt" | jq '.errorCode'
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/files/$OLD/download"
psql_q "select count(*) from files;"; echo "before=$BEFORE"
docker compose start minio
```

판정: 업로드는 `FILE_UPLOAD_FAILED`(500)이고 `files` 행 수가 늘지 않는다. 기존 파일 다운로드는 현재 구현상 `404 RESOURCE_NOT_FOUND`다(저장소 장애와 객체 없음을 구분하지 않음, 결과에 한계로 기록).

### V-FAIL-05 backend 재시작과 데이터 유지

```bash
docker compose restart backend
until curl -sf "$BASE_URL/api/health" > /dev/null; do sleep 2; done
api "$TOKEN" "$BASE_URL/api/files" | jq '.data.items | length'
TOKEN=$(login "$USER_EMAIL"); echo "${TOKEN:0:20}..."
```

판정: 재시작 전 발급한 토큰이 유효하고(JWT는 무상태), 파일 목록이 유지된다. `docker compose down` 후 `up`해도 볼륨 데이터가 유지된다(`down -v`는 초기화).

## 6. 관리자 정합성 점검

### V-ADMIN-01 관리자 계정 준비와 권한

회원가입은 `USER`만 만들 수 있으므로 DB에서 역할을 바꾼 뒤 다시 로그인한다(토큰에 역할이 들어간다).

```bash
ADMIN_EMAIL="admin-$RUN_ID@example.com"; signup "$ADMIN_EMAIL" "관리자" > /dev/null
psql_q "update users set role='ADMIN' where email='$ADMIN_EMAIL';"
ADMIN_TOKEN=$(login "$ADMIN_EMAIL")
api "$TOKEN" "$BASE_URL/api/admin/storage/orphans" | jq '.errorCode'
api "$ADMIN_TOKEN" "$BASE_URL/api/admin/storage/orphans?minAgeMinutes=1" | jq '.errorCode'
```

판정: 일반 사용자 `AUTH_ACCESS_DENIED`(403), 처리 중 판단 기준(기본 15분)보다 짧은 `minAgeMinutes`는 `VALIDATION_ERROR`(400).

### V-ADMIN-02 고아 객체 보고와 정리

V-FAIL-03에서 남은 객체를 대상으로 한다. 기다리는 시간을 줄이려면 `.env`에 `APP_FILE_IDEMPOTENCY_IN_PROGRESS_TIMEOUT=PT1M`을 넣고 `docker compose up -d backend`로 다시 띄운 뒤 `minAgeMinutes=2`를 쓴다(검증 후 원래 값으로 돌린다).

```bash
MIN_AGE=${MIN_AGE:-60}
api "$ADMIN_TOKEN" "$BASE_URL/api/admin/storage/orphans?minAgeMinutes=$MIN_AGE" | jq '.data | {orphans: [.orphanObjects[].storagePath], stale: [.staleUploadRequests[].storagePath]}'
api "$ADMIN_TOKEN" -X POST "$BASE_URL/api/admin/storage/orphans/cleanup?minAgeMinutes=$MIN_AGE" | jq '.data'
api "$ADMIN_TOKEN" "$BASE_URL/api/admin/storage/orphans?minAgeMinutes=$MIN_AGE" | jq '.data.orphanObjects | length'
```

판정: V-FAIL-03의 객체가 보고되고 정리 후 0개가 된다. `files` 행이 있는 객체(논리 삭제 포함)와 `minAgeMinutes`보다 최근 객체는 보고되지 않는다.

## 7. 데이터 이전 검증

MIG-01에서 데이터를 옮기지 않기로 해서([DEC-007](../DECISIONS.md#결정-목록)) 현재 범위에서는 실행하지 않는다. 데이터 이전을 다시 범위에 넣을 때 쓰는 공통 판정이다.

### V-MIG-01 이전 전 기준값 (온프레미스)

```bash
psql_q "select count(*) from users;"  > mig-before.txt
psql_q "select status, count(*) from files group by status order by 1;" >> mig-before.txt
psql_q "select id, storage_path, size from files order by id;" > mig-files-before.txt
mc_cmd "mc ls --recursive local/${MINIO_BUCKET:-files}" | wc -l >> mig-before.txt
```

샘플 파일 3개 이상을 다운로드해 해시를 저장한다(V-FUNC-02의 다운로드 명령).

### V-MIG-02 이전 후 비교 (AWS)

- `users` 수, 상태별 `files` 수가 이전 전과 같다.
- 모든 `files.storage_path`가 `files/`로 시작하고 각 키에 대해 `aws s3api head-object`가 성공한다. 크기가 `files.size`와 같다.
- 이전한 사용자로 기존 비밀번호 로그인이 된다(비밀번호 해시 보존).
- 샘플 파일을 AWS API로 다운로드한 해시가 이전 전 해시와 같다.
- 논리 삭제된 파일은 AWS에서도 조회되지 않는다.
- 이전 후 새 업로드의 `fileId`가 기존 최대값보다 크다(시퀀스 보존).
- 복사한 객체에도 Lambda 태그(`scan-status` 등)가 기록된다.

## 7A. AWS 배포 전 로컬 검증

AWS에 apply하기 전에, 온프레미스 시나리오(V-ONP~V-ADMIN)로는 확인되지 않는 AWS 전용 경로를 로컬에서 먼저 확인한다. 비용이 없고 실제 AWS를 호출하지 않는다. 온프레미스 기준선(ONP-01~03)이 이미 통과한 상태를 전제로 한다.

| ID | 확인 대상 | AWS에서 달라지는 점 |
| --- | --- | --- |
| V-LOC-01 | 자동 테스트·Terraform 정적 검사 | 리전 기본값 변경, 계층 분리, Owner 태그 |
| V-LOC-02 | `s3` 프로필 기능 | ECS는 MinIO 대신 AWS SDK S3 경로(`S3FileStorage`), 키 접두사 `files/` |
| V-LOC-03 | 컨테이너 실행 조건 | Fargate 0.5 vCPU·1GiB, 비root, ALB health check `/api/health` |
| V-LOC-04 | DB 재개 지연 | Aurora가 0 ACU로 일시정지돼 있으면 첫 연결이 재개될 때까지 기다린다 |
| V-LOC-05 | Lambda 후처리 | S3 이벤트로 객체 태그 기록 |
| V-LOC-06 | CD task definition 태그 유지 | 새 revision이 Owner 태그를 잃으면 정리 정책에 삭제될 수 있다 |

로컬에서 재현하지 않는 것: ElastiCache TLS 연결(`REDIS_SSL_ENABLED=true`), IAM task role 권한, Aurora 엔진 자체의 재개 시간, ALB·NAT 네트워크. 이것들은 [8. AWS 검증](#8-aws-검증)에서 본다.

공통 준비([0. 공통 준비](#0-공통-준비))에 더해 다음을 쓴다. `docker-compose.aws-local.yml`이 backend를 ECS와 같은 프로필·자원 한도로 바꾸고 S3 요청을 MinIO로 보낸다. 컨테이너에는 사용자 `~/.aws` 자격 증명이 들어가지 않는다.

```bash
DC="docker compose -f docker-compose.yml -f docker-compose.aws-local.yml"
AWS_LOCAL_BUCKET=${AWS_LOCAL_BUCKET:-fileshare-local}
```

### V-LOC-01 자동 테스트와 Terraform 정적 검사

WSL에 Java가 없으면 Temurin 17을 받아 `JAVA_HOME`과 `PATH`에 지정한 뒤 실행한다. Docker는 필요 없다.

```bash
(cd backend && ./gradlew test --no-daemon)
terraform fmt -check -recursive terraform
for env in dev-data dev; do
  terraform -chdir=terraform/environments/$env init -backend=false -input=false > /dev/null
  terraform -chdir=terraform/environments/$env validate
done
terraform -chdir=terraform/environments/dev-data plan -input=false | grep -E '^Plan:'
```

판정:

- 테스트 실패·오류가 0이다.
- fmt 차이가 없고 두 계층 모두 `Success! The configuration is valid.`가 나온다.
- 유지 계층을 apply하기 전에는 `Plan: 27 to add, 0 to change, 0 to destroy.`가 나온다. 실습 계층(`dev`) plan은 유지 계층 리소스를 조회하므로 apply 뒤에만 실행한다.
- plan은 AWS 조회만 하고 리소스를 만들지 않는다.

### V-LOC-02 `s3` 프로필 기능

```bash
mc_cmd "mc mb --ignore-existing local/$AWS_LOCAL_BUCKET"
$DC up -d --build minio backend
$DC exec -T backend env | grep -E '^(SPRING_PROFILES_ACTIVE|AWS_ENDPOINT_URL_S3|S3_BUCKET)='
curl -s "$BASE_URL/api/health" | jq
```

그다음 같은 셸에서 [V-FUNC-01](#v-func-01-회원가입로그인사용자-조회)~[V-FUNC-04](#v-func-04-삭제)와 [V-IDEM-01](#v-idem-01-같은-키-재전송)을 그대로 실행한다. 객체 확인은 V-DATA-01 대신 아래 명령을 쓴다.

```bash
STORAGE_PATH=$(psql_q "select storage_path from files where id=$FILE_ID;")
echo "$STORAGE_PATH"
mc_cmd "mc stat local/$AWS_LOCAL_BUCKET/$STORAGE_PATH"
mc_cmd "mc cat local/$AWS_LOCAL_BUCKET/$STORAGE_PATH" | sha256sum
$DC logs backend --since 10m | grep -iE 'SdkClientException|Unable to load credentials|UnknownHost' || echo "SDK 오류 없음"
```

판정:

- backend 환경변수에 `postgres,redis,s3`가 있고, health가 `UP`이다.
- V-FUNC·V-IDEM-01 판정이 온프레미스와 같다.
- `storage_path`가 `files/<uuid>.<확장자>`이다. 같은 키의 객체가 `$AWS_LOCAL_BUCKET`에 있고 해시가 원본과 같다.
- backend 로그에 SDK 자격 증명·호스트 해석 오류가 없다.

실패할 때 먼저 볼 것:

- `UnknownHostException: <bucket>.minio`이면 bucket 이름과 MinIO alias가 다른 것이다. `AWS_LOCAL_BUCKET`을 compose와 셸에서 같은 값으로 쓴다.
- `NoSuchBucket`이면 `mc mb`를 빠뜨린 것이다. `s3` 프로필은 bucket을 만들지 않는다. AWS에서는 유지 계층 Terraform이 만든다.

### V-LOC-03 컨테이너 실행 조건

V-LOC-02 스택에서 실행한다.

```bash
$DC exec -T backend id -u
docker inspect -f 'cpus={{.HostConfig.NanoCpus}} mem={{.HostConfig.Memory}} oom={{.State.OOMKilled}} restarts={{.RestartCount}}' "$($DC ps -q backend)"
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' "$($DC ps -q backend)"
$DC exec -T backend bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /api/health HTTP/1.0\r\n\r\n" >&3; head -1 <&3'
```

판정:

- uid가 `10001`이다(비root).
- `cpus=500000000 mem=1073741824`이고, `oom=false`, `restarts=0`이다.
- V-LOC-02를 마친 뒤 메모리 사용량이 1GiB 한도 안이다. 수치를 기록해 둔다.
- nginx를 거치지 않고 컨테이너 8080 포트에서 `/api/health`가 `200`이다. ALB target group은 이 경로를 직접 호출한다.

### V-LOC-04 DB 재개 지연

Aurora는 0 ACU로 멈춰 있다가 첫 연결이 오면 재개한다. 그동안 연결은 응답 없이 기다린다. PostgreSQL 컨테이너를 `pause`해 같은 상황을 만든다. 일시정지 길이 20초는 재개 시간 추정값(약 15초)보다 조금 길게 잡은 값이다.

**4-1. 실행 중 요청**

```bash
EMAIL4="loc4-$RUN_ID@example.com"; signup "$EMAIL4" loc4 > /dev/null
docker compose pause postgres
( sleep 20; docker compose unpause postgres ) &
time (login "$EMAIL4" | cut -c1-20)
wait
```

**4-2. 기동 중 DB가 멈춰 있을 때**

backend 컨테이너는 ID로 직접 시작한다. `$DC start backend`는 의존 서비스 postgres까지 시작하려다 paused 상태라 거부된다. JVM 기동에 16~20초가 걸리므로 pause는 40초로 잡는다. 20초면 DB 연결 시도 전에 풀려 이 상황을 재현하지 못한다(2026-11-19 확인).

```bash
BID=$($DC ps -aq backend)
docker stop "$BID" > /dev/null
docker compose pause postgres
echo "start   $(date -u +%T)"; docker start "$BID" > /dev/null
sleep 40; docker compose unpause postgres; echo "unpause $(date -u +%T)"
for i in $(seq 1 30); do curl -sf "$BASE_URL/actuator/health" > /dev/null && { echo "UP after ${i}x5s"; break; }; sleep 5; done
docker inspect -f 'state={{.State.Status}} exit={{.State.ExitCode}} restarts={{.RestartCount}}' "$BID"
docker logs --since 3m "$BID" 2>&1 | grep -E 'HikariPool-1 - Starting|connection attempt failed|Application run failed|Started File' | cut -c1-140
$DC up -d backend      # 종료됐으면 다시 띄운다
```

판정:

- 4-1: 로그인이 실패하지 않고 토큰을 받는다. 걸린 시간이 약 20초이고 30초(Hikari 기본 `connectionTimeout`) 안이다.
- 4-2: 먼저 `HikariPool-1 - Starting` 시각이 unpause보다 앞인지 본다. 뒤라면 재현되지 않은 것이므로 pause를 늘려 다시 한다. Spring Boot 3.5(HikariCP 6.3)에서는 연결 시도가 약 6초씩 두 번 실패한 뒤 `Application run failed`로 종료(exit 1)됐다(2026-11-19). Spring Boot 4.1(HikariCP 7.0, Hibernate 7.4)에서는 실패 없이 DB가 풀릴 때까지 기다린 뒤 기동해 `UP`이 됐다(pause 40초·70초, 2026-11-23). 어느 라이브러리 변경 때문인지는 확인하지 않았다. 기동 시 DB 재시도는 넣지 않았고, AWS에서는 배포 전에 DB를 깨워 이 상황을 피한다([DEC-010](../DECISIONS.md#결정-목록), [배포 전 DB 깨우기](DEPLOYMENT.md#46-github-actions-배포-실행)). 결과가 바뀌면(예: running으로 `UP`) 그 결과와 원인을 기록한다.

### V-LOC-05 Lambda 후처리

Lambda 핸들러를 로컬 Python에서 실행한다. S3 대신 MinIO(path 방식)를 쓴다. 임시 설정 파일을 쓰므로 사용자 `~/.aws`와 실제 AWS 계정은 사용하지 않는다.

```bash
LT=$(mktemp -d)
printf '[default]\ns3 =\n    addressing_style = path\n' > "$LT/config"
mc_cmd "echo clean | mc pipe local/$AWS_LOCAL_BUCKET/files/loc5-$RUN_ID.txt && echo x | mc pipe local/$AWS_LOCAL_BUCKET/files/loc5-$RUN_ID.sh"
jq -n --arg b "$AWS_LOCAL_BUCKET" --arg r "$RUN_ID" '{Records: [
  {eventName:"ObjectCreated:Put", s3:{bucket:{name:$b}, object:{key:("files/loc5-"+$r+".txt")}}},
  {eventName:"ObjectCreated:Put", s3:{bucket:{name:$b}, object:{key:("files/loc5-"+$r+".sh")}}},
  {eventName:"ObjectRemoved:Delete", s3:{bucket:{name:$b}, object:{key:"files/ignored.txt"}}}]}' > "$LT/event.json"

env -u AWS_PROFILE AWS_CONFIG_FILE="$LT/config" AWS_SHARED_CREDENTIALS_FILE=/dev/null \
  AWS_ACCESS_KEY_ID="${MINIO_ROOT_USER:-minioadmin}" AWS_SECRET_ACCESS_KEY="${MINIO_ROOT_PASSWORD:-minioadmin}" \
  AWS_DEFAULT_REGION=us-east-1 AWS_ENDPOINT_URL_S3="http://localhost:${MINIO_API_PORT:-9000}" AWS_EC2_METADATA_DISABLED=true \
  uv run --no-project --with boto3 python -c '
import json, sys
sys.path.insert(0, "terraform/functions/file-processor")
import handler
print(json.dumps(handler.lambda_handler(json.load(open(sys.argv[1])), None), indent=2))' "$LT/event.json"

mc_cmd "mc tag list local/$AWS_LOCAL_BUCKET/files/loc5-$RUN_ID.txt; mc tag list local/$AWS_LOCAL_BUCKET/files/loc5-$RUN_ID.sh"
rm -rf "$LT"
```

판정:

- `processed`에 2건이 있고 `ObjectRemoved` 이벤트는 건너뛴다.
- `.txt`: `scan-status=CLEAN`, `processing-status=COMPLETED`, `metadata-size=6`.
- `.sh`: `scan-status=FAILED`, `processing-status=QUARANTINED`.
- 다섯 태그(`scan-status`, `processing-status`, `metadata-size`, `metadata-content-type`, `processor`)가 모두 있다.

### V-LOC-06 CD task definition 태그 유지

[CI/CD workflow](../../.github/workflows/backend-ci-cd.yml)의 "Read current ECS task definition" 단계와 같은 jq를 예시 JSON에 적용한다.

```bash
CT=$(mktemp -d)
cat > "$CT/describe.json" <<'JSON'
{"taskDefinition":{"family":"fileshare-dev-backend","containerDefinitions":[{"name":"backend","image":"old"}]},
 "tags":[{"key":"Owner","value":"example-owner"},{"key":"Project","value":"fileshare"}]}
JSON
owner_check() { jq -e '.tags | any(.key == "Owner" and (.value | length > 0))' "$1" > /dev/null && echo pass || echo refuse; }

jq '.taskDefinition + {tags: (.tags // [])}' "$CT/describe.json" > "$CT/with.json"
jq -c '{family, tags}' "$CT/with.json"; owner_check "$CT/with.json"

jq 'del(.tags)' "$CT/describe.json" | jq '.taskDefinition + {tags: (.tags // [])}' > "$CT/none.json"
owner_check "$CT/none.json"

jq '.tags = [{"key":"Owner","value":""}]' "$CT/describe.json" | jq '.taskDefinition + {tags: (.tags // [])}' > "$CT/empty.json"
owner_check "$CT/empty.json"
rm -rf "$CT"
```

판정: 태그가 있는 경우 `family`와 태그 2개가 유지되고 `pass`가 나온다. 태그가 없거나 Owner 값이 빈 경우는 `refuse`가 나온다(workflow는 여기서 실패한다). 렌더·배포 action이 `tags`를 그대로 등록하는지는 첫 CD 실행에서 확인한다.

### V-LOC 정리

```bash
docker compose up -d --build minio backend      # override 없이 다시 만들어 minio 프로필로 되돌린다
docker compose exec -T backend env | grep '^SPRING_PROFILES_ACTIVE='
```

판정: `postgres,redis,minio`로 돌아온다. `$AWS_LOCAL_BUCKET` bucket과 V-LOC 계정·파일은 로컬 데이터라 남겨도 된다. 지우려면 `mc_cmd "mc rb --force local/$AWS_LOCAL_BUCKET"`를 실행한다.

## 8. AWS 검증

[AWS 배포](DEPLOYMENT.md#4-aws-배포) 이후 실행한다. 비용이 발생하므로 PORT-01의 예산·정리 조건 안에서만 수행한다.

```bash
cd terraform/environments/dev
BASE_URL=$(terraform output -raw application_url)
AWS_REGION=${AWS_REGION:-us-east-1}
FILE_BUCKET=$(terraform output -raw file_bucket_name)
FILE_PROCESSOR_FUNCTION=$(terraform output -raw file_processor_function_name)
FILE_PROCESSOR_LOG_GROUP=$(terraform output -raw file_processor_log_group_name)
ECS_CLUSTER=$(terraform output -raw ecs_cluster_name)
ECS_SERVICE=$(terraform output -raw ecs_service_name)
cd -
```

### V-AWS-01 진입점과 대상 상태

```bash
curl -s "$BASE_URL/api/health" | jq
aws ecs describe-services --region "$AWS_REGION" --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE" \
  --query "services[0].{desired:desiredCount,running:runningCount,taskDefinition:taskDefinition}"
```

판정: health `UP`, running = desired, ALB target group의 target이 `healthy`(콘솔 EC2 > Target Groups). 도메인 변수를 설정했다면 HTTP 요청이 HTTPS로 301 redirect된다.

### V-AWS-02 기능·계약 비교

2~4장의 V-FUNC, V-IDEM을 같은 `BASE_URL`로 실행한다. DB·Redis 직접 조회(`psql_q`, `redis_cmd`)는 AWS에서 쓸 수 없으므로 V-DATA는 다음으로 대신한다. Aurora 직접 조회는 MIG-01에서 정한 접근 경로가 있을 때만 수행한다.

```bash
STORED=$(api "$TOKEN" "$BASE_URL/api/files/$FILE_ID" | jq -r '.data.storedFileName')
aws s3api head-object --region "$AWS_REGION" --bucket "$FILE_BUCKET" --key "$STORED" --query '{size:ContentLength,type:ContentType}'
```

판정: 응답 형식·오류 코드가 ONP-02 기준선과 같다. S3 키는 `files/<uuid>.<확장자>`이며 `storedFileName`과 같다(S3 adapter는 두 값을 같게 저장한다). 차이는 작업일지에 그대로 기록한다.

### V-AWS-03 S3 이벤트 후처리

```bash
aws s3api get-object-tagging --region "$AWS_REGION" --bucket "$FILE_BUCKET" --key "$STORED"
aws logs filter-log-events --region "$AWS_REGION" --log-group-name "$FILE_PROCESSOR_LOG_GROUP" \
  --filter-pattern processed_objects --max-items 5
```

판정: 업로드 5~30초 안에 `scan-status`, `processing-status`, `metadata-size`, `metadata-content-type`, `processor` 태그가 생기고 Lambda 로그에 `processed_objects`가 남는다. 업로드 API 로그(`/ecs/fileshare-dev-backend`)와 후처리 로그가 분리된다. 현재 Lambda는 DB `status`·`scan_status`를 바꾸지 않는다(FILE-03).

### V-AWS-04 ECS task 중지와 자동 복구

```bash
TASK=$(aws ecs list-tasks --region "$AWS_REGION" --cluster "$ECS_CLUSTER" --service-name "$ECS_SERVICE" \
  --desired-status RUNNING --query 'taskArns[0]' --output text)
aws ecs stop-task --region "$AWS_REGION" --cluster "$ECS_CLUSTER" --task "$TASK" --reason "V-AWS-04 recovery test"
aws ecs wait services-stable --region "$AWS_REGION" --cluster "$ECS_CLUSTER" --services "$ECS_SERVICE"
curl -s -o /dev/null -w '%{http_code}\n' "$BASE_URL/api/health"
```

판정: running이 desired로 돌아오고 target이 다시 `healthy`, health `200`. 중지 원인이 ECS service event·stopped task·backend 로그 중 한 곳에서 보인다. 1 task 구성에서는 복구 동안 요청이 실패할 수 있으며 실패 시간을 기록한다.

### V-AWS-05 Lambda 실패 주입과 정상 재처리

```bash
printf '{"Records":[{"eventName":"ObjectCreated:Put"}]}' > lambda-fail.json
aws lambda invoke --region "$AWS_REGION" --function-name "$FILE_PROCESSOR_FUNCTION" \
  --payload fileb://lambda-fail.json lambda-fail-out.json | jq '.FunctionError'
aws logs filter-log-events --region "$AWS_REGION" --log-group-name "$FILE_PROCESSOR_LOG_GROUP" --filter-pattern ERROR --max-items 3
```

판정: `FunctionError`가 있고 로그에 오류가 남는다. 이후 정상 업로드(V-FUNC-02)의 태그가 다시 기록되고 API는 영향을 받지 않는다. S3 객체·DB는 바뀌지 않는다.

### V-AWS-06 알람

```bash
aws cloudwatch describe-alarms --region "$AWS_REGION" --alarm-name-prefix fileshare-dev \
  --query "MetricAlarms[].{name:AlarmName,state:StateValue}"
```

판정: `file-processor-errors`, `file-processor-duration`, `backend-cpu-high`, `backend-memory-high`, `alb-target-5xx` 알람이 있다. V-AWS-05 후 `file-processor-errors`가 평가 주기 안에 `ALARM`으로 바뀌거나 이력에 남는다. 알림 전달은 OBS-02 범위다.

## 9. 성능 smoke

### V-PERF-01 반복 호출

```bash
for i in $(seq 1 50); do curl -s -o /dev/null -w '%{http_code} %{time_total}\n' "$BASE_URL/api/health"; done | sort | uniq -c | head
for i in $(seq 1 20); do api "$TOKEN" -o /dev/null -w '%{http_code} %{time_total}\n' "$BASE_URL/api/files"; done
```

판정: 모두 `200`. 이상값은 온프레미스는 `docker compose logs`, AWS는 ALB target response time·backend 로그로 원인을 찾는다. 이 결과는 smoke이며 PERF-01의 부하 실험을 대신하지 않는다.

### V-PERF-02 업로드 자원 사용

```bash
head -c 9000000 /dev/urandom > "load-$RUN_ID.txt"
docker stats --no-stream backend &   # 온프레미스: 업로드 전후로 반복 실행
for i in $(seq 1 10); do api "$TOKEN" -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL/api/files" -F "file=@load-$RUN_ID.txt" & done; wait
docker stats --no-stream backend
```

판정: 모두 `200`, 메모리 사용 변화를 기록한다. S3 업로드의 전체 버퍼링 제거 효과(FILE-04)는 AWS에서 같은 조건으로 ECS 메모리 지표를 보고, 비교 기준이 필요하면 legacy 커밋 이미지와 비교한다.

## 10. 결과 기록과 판정

작업일지에는 시나리오마다 다음을 남긴다.

| 항목 | 예 |
| --- | --- |
| 시나리오 ID·환경 | V-FAIL-02 / 온프레미스 |
| 커밋·이미지 | `git rev-parse --short HEAD`, `docker compose images backend` |
| 실행 명령·주요 출력 | 응답 코드, 판정에 쓴 값 (토큰·비밀번호 제외) |
| 판정 | 통과 / 실패 / 일부 통과(이유) |
| 한계·후속 | 재현되지 않은 경합, 수동 대기 시간 등 |
| 정리 | 테스트 파일·계정·`.env` 임시 값 원복 여부 |

작업 완료 판정:

| 작업 | 통과해야 하는 시나리오 |
| --- | --- |
| ONP-01 | V-ONP-01 |
| ONP-02 | V-FUNC-01~04, V-DATA-01~02 |
| ONP-03 | V-IDEM-01~03, V-FAIL-01~05, V-ADMIN-01~02 |
| AWS 유지 계층 apply 전 | V-LOC-01~06 |
| MIG-02 | V-MIG-01~02 |
| VERIFY-01 | V-AWS-01~03, AWS에서의 V-FUNC·V-IDEM |
| VERIFY-02 | V-AWS-04~06, V-PERF-01 |
| OBS-01 | V-OBS-01~02 |
| OBS-02 | V-OBS-03 |
| OPS-01 | V-OPS-01~05 |
| OPS-02 | V-BAK-01~02 |
| BACKUP-01 | V-BAK-03~04 (2026-11-24 철거, 실행 안 함) |
| FILE-03 | V-SCAN-01~05 |
| CICD-02/03 | V-CICD-01~03 |
| IAC-03 | V-IAC-01~03 |
| PERF-01 | V-PERF-03 |

## 11. 관측 지표와 SLI

OBS-01에서 정한 지표·SLI다. 대시보드 `<prefix>-service`와 Logs Insights 저장 쿼리 `<prefix>/api-sli-by-route`(경로별 요청 수·성공률·p50/p95), `<prefix>/request-trace`(요청 ID 추적)로 본다. 알람 대응은 [알람 대응](DEPLOYMENT.md#411-1-알람-대응-obs-02)을 따른다.

| 신호 | 출처 | 용도 |
| --- | --- | --- |
| 경로별 요청 수·성공률·p50/p95 | backend 접근 로그(`event=http_request`, `route`·`status`·`durationMs`) | API별 SLI. 서버 처리 시간 |
| 요청 수·5xx(target/ELB)·응답 시간 p50/p95/p99 | ALB(`AWS/ApplicationELB`) | 진입점 SLI. ELB 5xx는 target 없음·연결 오류 |
| healthy/unhealthy target | ALB target group | 가용성 |
| CPU·메모리, running/desired task | ECS, Container Insights | 용량·복구 |
| ACU·연결 수 | Aurora(`AWS/RDS`) | DB 부하·일시정지 |
| hit/miss·연결 수 | ElastiCache | 캐시 효과 |
| 호출·오류·p95 | Lambda | 후처리 |

로그 규칙: 요청마다 `X-Request-Id`를 받거나(8~64자 `[A-Za-z0-9._-]`) 만들어 응답 헤더로 돌려주고 같은 요청의 모든 로그에 `requestId`로 붙인다. 접근 로그는 메서드·경로 패턴(ID 제외)·상태·처리 시간만 남긴다. 요청 본문·쿼리 문자열·토큰·비밀번호·파일 내용은 남기지 않는다. AWS는 JSON(`ecs` 형식), 로컬·온프레미스는 텍스트 형식이다.

SLI 정의(측정 근거는 작업일지, 아래 목표는 실습 환경 기준):

| SLI | 대상 | 측정 | 목표(SLO) | 기간 |
| --- | --- | --- | --- | --- |
| 조회 가용성 | `GET /api/files`, `GET /api/files/{fileId}` | 접근 로그의 비 5xx 비율 + ALB ELB 5xx | 99% | 실습 세션 단위 |
| 조회 지연 | 같은 경로 | 접근 로그 `durationMs` p95(서버 처리) | 200ms 이하 | 실습 세션 단위 |
| 전송 가용성 | `POST /api/files`, `GET /api/files/{fileId}/download` | 접근 로그 비 5xx 비율 | 99% | 실습 세션 단위 |
| 전송 지연 | 같은 경로, 10MB 이하 | `durationMs` p95 | 크기·클라이언트 대역폭에 좌우되므로 목표 없이 기록만 | - |

- 목표값은 2026-11-20 측정값에 여유를 둔 값이다. 실습 계층은 하루 몇 시간만 떠 있으므로 이 수치를 장기 가용성 실적으로 표시하지 않는다.
- 클라이언트가 보는 지연(로컬→us-east-1 왕복 약 0.4초 포함)은 서버 SLI와 섞지 않는다.

목표값을 이렇게 둔 이유와 판단:

| SLI | 2026-11-20 실측 | 목표 근거 | 판단 |
| --- | --- | --- | --- |
| 조회 가용성 | 100% (5xx 0건) | 하루 몇 시간만 켜는 환경이라 99.9%는 측정 자체가 의미 없다. 99%는 한 세션 수백 건 중 몇 건의 실패를 허용한다 | 세션 중 5xx가 생기면 알람(`alb-target-5xx`)과 같이 본다. task 교체 중 ELB 5xx는 서버 SLI가 아니라 진입점 가용성으로 따로 기록한다 |
| 조회 지연 p95 | 목록 10ms, 단건 8ms | 실측의 20배 여유. Aurora 재개 직후(첫 연결 수 초), 캐시 미스, GC를 감안했다 | 200ms를 넘으면 경로별 p95 → Aurora ACU·연결 → ECS CPU 순으로 본다 |
| 전송 지연 | 업로드 p95 약 10초, 다운로드 p95 101ms | 클라이언트 대역폭에 좌우되어 서버 품질 지표로 쓸 수 없다 | 크기별 처리량(MB/s)으로 기록하고 PERF-01에서 같은 조건으로 비교한다 |

- 측정 시 주의: 트래픽 직후에는 로그 수집이 덜 돼 건수가 적게 나온다(2026-11-20, 30초 뒤 88건 → 2분 뒤 104건). 1~2분 뒤에 쿼리하고, 정확한 시간 범위를 지정한다.
- 분당 접근 로그 수가 ALB `RequestCount`와 같은지(health check 분당 2회 차이)로 로그 유실이 없는지 확인한다.

### V-OBS-01 요청 ID 추적

```bash
RID="obs-$RUN_ID"
curl -s -D - -o /dev/null -H "X-Request-Id: $RID" -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/files" | grep -i x-request-id
# Logs Insights 저장 쿼리 <prefix>/request-trace에서 REPLACE_WITH_REQUEST_ID를 $RID로 바꿔 실행
```

판정: 응답 헤더가 보낸 값과 같고, 로그에서 같은 `requestId`의 접근 로그가 경로 패턴(`/api/files`)·상태·처리 시간과 함께 보인다. 토큰·본문은 로그에 없다.

### V-OBS-02 경로별 SLI

트래픽(V-FUNC·V-PERF)을 만든 뒤 `<prefix>/api-sli-by-route`를 실행한다.

판정: 경로가 ID 없는 패턴으로 묶이고 요청 수·성공률·p50/p95가 나온다. 대시보드의 ALB·ECS·Aurora·Redis·Lambda 위젯에 값이 있다.

### V-OBS-03 알람 발화·수신·복귀

V-AWS-05(Lambda 실패)와 V-AWS-04(task 중지)를 실행한다.

판정: `file-processor-errors`가 `ALARM` → 이메일 수신 → 오류가 멈추면 `OK` → 복귀 이메일 수신. task 중지 때 `alb-elb-5xx`(와 2분 넘게 중단되면 `alb-no-healthy-target`)가 같은 흐름을 따른다. 상태 이력은 `aws cloudwatch describe-alarm-history`로 남긴다.

## 12. 운영 보강 검증 (3단계)

2026-11-20에 추가한 시나리오다. 실행 결과는 [작업일지](../worklogs/2026-11-20.md#3단계-실행)에 있다. 모두 실습 계층이 떠 있을 때 실행하고, 주입한 장애는 같은 시나리오 안에서 되돌린다.

### V-OPS-01~05 장애 주입

| ID | 주입 | 되돌리기 | 판정 |
| --- | --- | --- | --- |
| V-OPS-01 | 없는 이미지 태그의 revision으로 운영 서비스 `update-service` | 직전 revision으로 `update-service` | 기존 task가 계속 응답(사용자 영향 없음), stopped `TaskFailedToStart`·`CannotPullContainerError`, circuit breaker가 있으면 자동 롤백 |
| V-OPS-02 | `DB_USERNAME`을 틀린 revision 배포 | 위와 같음 | 새 task exit 1·`password authentication failed`, 기존 task 유지 |
| V-OPS-03 | task role에서 `application-files` 정책 분리 | 정책 다시 연결 | 업로드 500 `FILE_UPLOAD_FAILED`, 다운로드 503 `FILE_STORAGE_UNAVAILABLE`(404가 아님), `X-Request-Id`로 원인 로그 추적, `alb-target-5xx` ALARM |
| V-OPS-04 | `aws elasticache reboot-cache-cluster` | 자동 | 조회·다운로드 200 유지(DB 우회), 지연 증가 기록 |
| V-OPS-05 | scalable target min 0, desired 0을 4분 | min 1, desired 1 | ALB 503, `alb-elb-5xx`와 `alb-no-healthy-target` ALARM → 복구 후 OK, 복구 시간 기록 |

각 시나리오는 동안 2초 간격으로 조회를 보내 비정상 응답 구간을 기록하고, 중지 사유는 `/aws/events/<prefix>-ecs-task-stopped`에서 확인한다.

### V-BAK-01~02 백업 복원

- V-BAK-01: [백업 복원](DEPLOYMENT.md#412-1-백업-복원-ops-02) 절차로 별도 클러스터에 PITR → 일회성 `psql` task로 복원본·운영본 `files` 행 수 비교 → 복원본 모든 `storage_path`의 S3 객체 존재·크기·표본 해시 확인 → 검증 클러스터 삭제. 판정: RPO(사고 시각 − `LatestRestorableTime`) 5분 이하, RTO(복원 시작 → 검증 완료) 60분 이하, 불일치 0.
- V-BAK-02: S3 객체 삭제 후 삭제 마커 제거로 복구. 판정: 같은 해시.
- V-BAK-03 (BACKUP-01, 2026-11-24 철거로 더 이상 실행하지 않음, [DEC-025](../DECISIONS.md#결정-목록)): file bucket에 `files/` 밖 시험 객체를 올리고 `ReplicationStatus`가 `COMPLETED`가 될 때까지 시간을 잰다 → 개인 계정 사본 `REPLICA`·해시 확인 → 원본 삭제(삭제 마커) 1분 뒤 개인 계정에 버전 1개·삭제 마커 0개 → 원본 시험 객체 버전 정리. 판정: 복제 완료, 해시 일치, 사본 유지.
- V-BAK-04 (BACKUP-01, 2026-11-24 철거로 더 이상 실행하지 않음, `scripts/db-backup.sh` 삭제): [개인 계정 백업](DEPLOYMENT.md#412-2-개인-계정-백업-backup-01)의 `scripts/db-backup.sh` 실행 → 두 컨테이너 exit 0, 원본·사본 SHA-256 일치, 사본 `pg_restore --list`에 테이블 데이터가 보임. file-processor Lambda 호출 수·검사 결과 큐·DLQ가 0이고 dump에 GuardDuty 태그가 없음. 판정: 모두 충족.

### V-SCAN-01~05 악성코드 검사 (FILE-03)

| ID | 동작 | 판정 |
| --- | --- | --- |
| V-SCAN-01 | 정상 텍스트 업로드 | 응답 `scanning`/`PENDING`, 그동안 다운로드 409 `FILE_SCAN_PENDING`, 수십 초 안에 `available`/`CLEAN`, 다운로드 200, 객체 태그에 `GuardDutyMalwareScanStatus=NO_THREATS_FOUND`와 `extension-check`가 함께 있음 |
| V-SCAN-02 | EICAR 시험 문자열 업로드 | `quarantined`/`INFECTED`, 다운로드 403 `FILE_QUARANTINED`, 태그 `THREATS_FOUND` |
| V-SCAN-03 | 끝난 파일에 반대 결과를 SQS로 다시 보냄(중복·역순) | 상태가 바뀌지 않음 |
| V-SCAN-04 | 업로드 직후 삭제 | 검사 결과가 와도 조회 404·목록 제외 유지 |
| V-SCAN-05 | DB에 없는 객체 키의 결과를 SQS로 보냄 | 재시도 뒤 DLQ로 이동, `scan-results-dlq` ALARM |

검사 시간 초과(15분)는 자동 테스트(`ScanResultServiceTests`)로 확인한다.

### V-CICD-01~03 승격·롤백

- V-CICD-01: main에 backend 변경 머지 → staging 배포·smoke → `production` 승인 → 배포·smoke·5xx 확인. 판정: 두 서비스가 같은 `taskDefinitionArn`(같은 digest), `latest`가 그 digest.
- V-CICD-02: Run workflow `fault=start-failure`, `fault_target=staging`. 판정: staging circuit breaker 롤백, workflow 실패, production 승인 요청 없음, 배포 실패 이메일.
- V-CICD-03: Run workflow `fault=runtime-failure`, `fault_target=production`. 판정: staging 통과 → 승인 → production smoke 실패(업로드 5xx) → workflow가 직전 revision으로 롤백, 롤백 후 smoke 정상.

### V-IAC-01~03 Terraform CI

- V-IAC-01: `terraform/` 변경 PR → static·plan(두 계층) 통과, plan role이 업로드 파일을 읽지 못함(`s3:GetObject` 거부).
- V-IAC-02: 실습 계층 리소스를 콘솔·CLI로 바꿈(예: 알람 임계값) → Run workflow(drift) → `drift (dev)` 실패와 변경 목록. 코드대로 되돌린 뒤 다시 실행해 통과.
- V-IAC-03: 변경 있는 plan의 main 머지 → `terraform-dev` 승인 → 저장된 plan apply. 변경 없는 머지에서는 apply job이 건너뛰어짐.

### V-PERF-03 부하·확장

ECS 일회성 task(k6)로 AWS 안에서 부하를 준다: 0→40 VU 2분, 40 VU 6분, 감소 1분, 요청 비율 단건 70%·목록 20%·100KB 업로드 10%. 같은 조건을 (a) task 1개 고정(scalable target max 1), (b) Auto Scaling 1~4로 두 번 실행한다. 판정: 처리량·오류율·p50/p95, task 수 변화와 확장·축소 시각, CPU를 기록하고 (a)와 비교한다. 축소는 부하 종료 뒤 15분 이상 걸린다.

실행: `python3 -I scripts/drills/perf01_load.py <out> baseline|scaling <owner-tag>`. 끝나면 `perf-*` 사용자의 행·객체 버전을 지운다. 2026-11-24 개인 계정 백업 철거([DEC-025](../DECISIONS.md#결정-목록)) 이후 복제를 멈추는 단계는 없다([정리](DEPLOYMENT.md#412-2-개인-계정-백업-backup-01)).

결과(2026-11-24): (a) 59.4 rps·오류 0·읽기 p95 1.78초, (b) 72.9 rps·오류 0·읽기 p95 1.78초, task 1 → 2 → 3. 확장 뒤 메모리 정책이 축소를 막아 고쳤다(DEC-023). 한계: 목록 API에 페이지 처리가 없어 시험 중 쌓이는 파일만큼 목록 응답이 무거워진다. 원본은 [perf-01](../assets/perf-01/), 해석은 [작업일지](../worklogs/2026-11-24.md#perf-01-부하확장-v-perf-03).
