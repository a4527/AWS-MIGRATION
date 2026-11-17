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

api "$TOKEN" "$BASE_URL/api/files" | jq '.data | length'
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
api "$TOKEN" "$BASE_URL/api/files" | jq "[.data[] | select(.fileId==$FILE_ID)] | length"
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
api "$TOKEN" "$BASE_URL/api/files" | jq '.data | length'
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

[데이터 이전 방식](../proposals/DATA-MIGRATION.md)이 MIG-01에서 확정된 뒤 실행한다. 아래는 방식과 무관한 공통 판정이다.

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

## 8. AWS 검증

[AWS 배포](DEPLOYMENT.md#4-aws-배포) 이후 실행한다. 비용이 발생하므로 PORT-01의 예산·정리 조건 안에서만 수행한다.

```bash
cd terraform/environments/dev
BASE_URL=$(terraform output -raw application_url)
AWS_REGION=${AWS_REGION:-ap-northeast-2}
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
| MIG-02 | V-MIG-01~02 |
| VERIFY-01 | V-AWS-01~03, AWS에서의 V-FUNC·V-IDEM |
| VERIFY-02 | V-AWS-04~06, V-PERF-01 |
