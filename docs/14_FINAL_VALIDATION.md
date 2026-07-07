# 14. Final Validation

Day 13 최종 검증은 온프레미스 Docker Compose 환경에서 구현한 기능 계약이 AWS ECS/서버리스 환경에서도 유지되는지 확인하고, AWS 전환으로 개선되는 운영 특성을 함께 검증한다.

검증의 핵심은 두 가지다.

- 사용자 관점의 API 동작은 온프레미스와 AWS에서 동일해야 한다.
- 운영 관점에서는 AWS의 관리형 서비스, 서버리스 후처리, Auto Scaling, CloudWatch 관측성, HTTPS/IAM 보안 이점이 온프레미스 구성보다 명확해야 한다.

실제 AWS 리소스 검증은 비용이 발생하므로 `docs/13_AWS_DEPLOYMENT_RUNBOOK.md` 순서대로 Terraform apply와 GitHub Actions 배포를 완료한 뒤 수행한다.

## 1. 검증 범위

기능 동일성:

- API 응답 계약이 온프레미스와 AWS에서 동일한지 확인한다.
- 업로드, 다운로드, 삭제의 사용자 관점 동작이 동일한지 확인한다.
- MinIO object key와 S3 object key가 같은 `storagePath` 계약을 유지하는지 확인한다.
- PostgreSQL과 Aurora PostgreSQL의 핵심 테이블 데이터 형태가 동일한지 확인한다.
- Redis와 ElastiCache의 캐시 키 형식이 동일한지 확인한다.

AWS 전환 효과:

- Nginx 단일 진입점이 ALB health check와 target group 기반 라우팅으로 대체되는지 확인한다.
- 수동 컨테이너 복구 중심의 온프레미스 운영이 ECS service rolling deployment와 task self-healing으로 대체되는지 확인한다.
- MinIO 기반 object 저장소가 S3 durability, prefix 기반 IAM 제한, event notification으로 확장되는지 확인한다.
- 파일 후처리가 애플리케이션 동기 처리나 수동 작업이 아니라 S3 event 기반 Lambda로 분리되는지 확인한다.
- 로컬 로그 확인 중심의 장애 분석이 CloudWatch Logs와 CloudWatch Alarm 기반 관측성으로 확장되는지 확인한다.
- HTTP/Nginx 중심 진입점이 Route53, ACM, ALB HTTPS 구성으로 보안 강화되는지 확인한다.

## 2. 온프레미스와 AWS 비교 요약

| 영역 | 온프레미스 기준 | AWS 기준 | 부각할 개선점 |
| --- | --- | --- | --- |
| 진입점 | Nginx reverse proxy | ALB, Route53, ACM | managed load balancing, HTTPS, target health |
| 실행 환경 | Docker Compose backend container | ECS Fargate service | task self-healing, rolling deployment, Auto Scaling |
| 파일 관리 | MinIO bucket과 backend 중심 처리 | S3, object tag, S3 event, Lambda | 저장/후처리/추적 책임 분리 |
| DB | PostgreSQL container | Aurora PostgreSQL | managed database, subnet isolation |
| 캐시 | Redis container | ElastiCache Redis | managed cache, TLS 연결 |
| 보안 | 로컬 네트워크와 컨테이너 환경변수 중심 | IAM role, SG, private subnet, ACM HTTPS | 최소 권한, 네트워크 격리, 전송 구간 암호화 |
| 비용 관리 | 서버와 컨테이너를 직접 상시 운영 | desired count, Serverless, managed metric | 사용량 기반 조정, 리소스별 비용 추적 |
| 장애 복구 | 컨테이너 상태 확인 후 수동 재시작 | ECS scheduler, ALB health, managed service | 자동 교체, rolling deployment, 장애 위치 분리 |
| 로그/알람 | `docker compose logs` | CloudWatch Logs/Alarms | 중앙화된 로그, 지표 기반 장애 감지 |
| 확장 | compose scale 또는 수동 증설 | ECS Service Auto Scaling | CPU/memory 기반 자동 확장 |

## 3. AWS 전환 가치 검증 매트릭스

상세 실행 절차는 5장 이후에서 한 번만 다룬다. 이 장은 최종 검증에서 어떤 운영 개선을 증명해야 하는지 정리한다.

| 검증 영역 | 확인 위치 | 완료 기준 |
| --- | --- | --- |
| 파일 관리 | API 응답, Aurora `files.storage_path`, S3 object/tag, Lambda log | DB metadata와 object key가 일치하고 후처리 결과가 S3 tag와 Lambda log에 남는다. |
| 비용 관리 | Terraform 변수, ECS desired/min/max, Lambda invocation, log retention | 상시 실행 리소스와 이벤트 기반 실행 리소스를 구분하고 dev 환경 비용 상한을 설명할 수 있다. |
| 보안 경계 | subnet, security group, IAM role, ALB listener, ACM | ALB는 public, ECS는 private, DB/Redis는 database subnet에 있고 S3 권한은 `files/` prefix로 제한된다. |
| 장애 복구 | ECS event, ALB target health, CloudWatch Logs/Alarms | 실제 장애 주입 후 실패 로그를 찾고, scheduler 또는 정상 재시도 이후 health/API가 복구된다. |
| 관측성 | backend log group, Lambda log group, CloudWatch alarm/metric | API 처리와 파일 후처리 로그가 분리되고 장애 원인을 로그와 지표로 추적할 수 있다. |
| 확장성 | ECS Service Auto Scaling, ALB target group, Lambda metrics | API 서버, object 저장소, 후처리 실행 단위의 확장 책임을 분리해서 설명할 수 있다. |

## 4. 환경별 진입점

온프레미스:

```bash
ONPREM_BASE_URL=http://localhost:8080
```

AWS:

```bash
cd terraform/environments/dev
AWS_BASE_URL=$(terraform output -raw application_url)
AWS_REGION=${AWS_REGION:-ap-northeast-2}
FILE_BUCKET=$(terraform output -raw file_bucket_name)
FILE_PROCESSOR_FUNCTION=$(terraform output -raw file_processor_function_name)
FILE_PROCESSOR_LOG_GROUP=$(terraform output -raw file_processor_log_group_name)
```

도메인/HTTPS를 설정하지 않은 dev 환경에서는 `application_url`이 ALB HTTP URL을 반환한다. HTTPS가 설정된 환경에서는 같은 API 경로를 HTTPS 기준으로 호출한다.

## 5. 공통 API 비교

두 환경에서 같은 순서로 실행한다.

```bash
BASE_URL="$ONPREM_BASE_URL"
```

또는:

```bash
BASE_URL="$AWS_BASE_URL"
```

Health:

```bash
curl -s "$BASE_URL/api/health"
curl -s "$BASE_URL/actuator/health"
```

인증:

```bash
curl -s -X POST "$BASE_URL/api/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "day13-user@example.com",
    "password": "Password123!",
    "name": "Day13 User"
  }'

TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "day13-user@example.com",
    "password": "Password123!"
  }' | jq -r '.data.accessToken')

curl -s "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TOKEN"
```

정상 기준:

- `success`가 `true`다.
- 로그인 응답에 `data.accessToken`이 있다.
- 현재 사용자 조회의 email, name, role 형식이 두 환경에서 동일하다.

## 6. 업로드/다운로드 시나리오

테스트 파일:

```bash
printf "day13 file validation\n" > day13-sample.txt
```

업로드:

```bash
UPLOAD_RESPONSE=$(curl -s -X POST "$BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./day13-sample.txt" \
  -F "description=day13 validation")

echo "$UPLOAD_RESPONSE"
FILE_ID=$(echo "$UPLOAD_RESPONSE" | jq -r '.data.fileId')
```

목록/단건 조회:

```bash
curl -s "$BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN"

curl -s "$BASE_URL/api/files/$FILE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

다운로드:

```bash
curl -s -L "$BASE_URL/api/files/$FILE_ID/download" \
  -H "Authorization: Bearer $TOKEN" \
  -o day13-downloaded.txt

diff -u day13-sample.txt day13-downloaded.txt
```

삭제:

```bash
curl -s -X DELETE "$BASE_URL/api/files/$FILE_ID" \
  -H "Authorization: Bearer $TOKEN"

curl -s "$BASE_URL/api/files/$FILE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

정상 기준:

- 업로드 응답의 `originalFileName`은 `day13-sample.txt`다.
- 다운로드 파일은 원본과 `diff` 차이가 없다.
- 삭제 후 단건 조회는 `RESOURCE_NOT_FOUND` 계열 응답을 반환한다.

## 7. 저장소와 서버리스 후처리 비교

온프레미스:

- PostgreSQL `files.storage_path` 값과 MinIO object key가 일치한다.
- Redis 키는 `files:metadata:{fileId}` 형식이다.
- MinIO bucket은 `files`이며 object는 기존 storage path 아래 생성된다.
- 파일 후처리는 백엔드 구현 또는 수동 검증 범위에 머문다.

AWS:

- Aurora PostgreSQL의 `files.storage_path` 값과 S3 object key가 일치한다.
- S3 object key는 기본적으로 `files/` prefix 아래 생성된다.
- ElastiCache Redis 키는 `files:metadata:{fileId}` 형식이다.
- S3 object tag에 `scan-status`, `processing-status`, `metadata-size`, `metadata-content-type`, `processor`가 기록된다.
- S3 `ObjectCreated` 이벤트가 Lambda를 호출하므로 파일 후처리 책임이 backend API 응답 경로에서 분리된다.
- Lambda 실패는 backend API 로그가 아니라 Lambda CloudWatch log와 alarm에서 독립적으로 추적한다.

AWS 확인 절차:

1. 업로드 응답에서 file ID를 변수로 잡는다.

```bash
echo "$UPLOAD_RESPONSE" | jq
FILE_ID=$(echo "$UPLOAD_RESPONSE" | jq -r '.data.fileId')
```

2. Aurora PostgreSQL에서 `storage_path`를 확인한다.

```sql
select id, original_file_name, storage_path, status, scan_status
from files
where id = '<FILE_ID>';
```

DB는 database subnet에 있으므로 로컬 PC에서 바로 접속하지 않는다. Session Manager bastion, ECS exec, 별도 운영 접속 경로처럼 private subnet 접근 경로가 있을 때만 직접 조회한다. 직접 접속 경로가 없으면 API 단건 조회와 S3 object key 확인으로 먼저 검증한다.

3. S3에서 `files/` prefix 아래 object가 생성됐는지 확인한다.

```bash
aws s3api list-objects-v2 \
  --region "$AWS_REGION" \
  --bucket "$FILE_BUCKET" \
  --prefix "files/" \
  --query "Contents[].Key" \
  --output text
```

콘솔에서는 S3 > Buckets > `<file_bucket_name>` > Objects > `files/` 경로를 연다. 업로드한 파일의 object key가 `files/`로 시작해야 한다.

4. Aurora의 `files.storage_path`와 S3 object key가 같은지 확인한다.

```bash
STORAGE_PATH="<Aurora files.storage_path 값>"

aws s3api head-object \
  --region "$AWS_REGION" \
  --bucket "$FILE_BUCKET" \
  --key "$STORAGE_PATH"
```

`head-object`가 성공하면 DB metadata가 가리키는 object가 실제 S3 bucket에 존재한다. `NoSuchKey`가 나오면 DB의 `storage_path`, 애플리케이션 `S3_OBJECT_PREFIX`, Lambda notification prefix를 함께 확인한다.

5. S3 object tag에 Lambda 후처리 결과가 기록됐는지 확인한다.

```bash
aws s3api get-object-tagging \
  --region "$AWS_REGION" \
  --bucket "$FILE_BUCKET" \
  --key "$STORAGE_PATH"
```

콘솔에서는 S3 > Buckets > `<file_bucket_name>` > Objects > `<object key>` > Properties > Tags에서 확인한다. 정상 기준은 `scan-status`, `processing-status`, `metadata-size`, `metadata-content-type`, `processor` tag가 존재하는 것이다.

6. Lambda가 S3 ObjectCreated 이벤트로 실행됐는지 확인한다.

```bash
aws logs describe-log-streams \
  --region "$AWS_REGION" \
  --log-group-name "$FILE_PROCESSOR_LOG_GROUP" \
  --order-by LastEventTime \
  --descending \
  --max-items 5
```

콘솔에서는 CloudWatch > Log groups > `/aws/lambda/fileshare-dev-file-processor`에서 업로드 시간대의 log stream을 확인한다. Lambda > Functions > `<file_processor_function_name>` > Monitor에서도 Invocations가 증가해야 한다.

7. backend 로그와 Lambda 로그가 분리되는지 확인한다.

```bash
aws logs describe-log-streams \
  --region "$AWS_REGION" \
  --log-group-name "/ecs/fileshare-dev-backend" \
  --order-by LastEventTime \
  --descending \
  --max-items 5
```

업로드 API 요청 로그는 `/ecs/fileshare-dev-backend`, S3 object 후처리 로그는 `/aws/lambda/fileshare-dev-file-processor`에서 따로 확인한다.

AWS 개선 확인 기준:

- 파일 업로드 후 backend task를 직접 확장하지 않아도 Lambda가 별도 실행 단위로 후처리를 수행한다.
- S3 object tag만 확인해도 후처리 결과를 추적할 수 있다.
- S3 object 접근 권한은 bucket 전체가 아니라 `files/` prefix 기준으로 제한된다.

## 8. 장애 복구 시나리오

온프레미스 장애 점검은 `docs/11_ONPREM_MANUAL_TEST.md`의 장애 시나리오 수동 점검을 따른다.

AWS 장애 점검은 실제 실패를 한 번 만든 뒤 로그를 확인하고 정상 동작으로 복구되는지까지 본다. 운영 공유 환경에서는 사전에 영향 시간을 공지하고, dev 환경에서만 수행한다.

### 8.1 ECS task 중지 실패 주입

```bash
ECS_CLUSTER=$(terraform output -raw ecs_cluster_name)
ECS_SERVICE=$(terraform output -raw ecs_service_name)

RUNNING_TASK_ARN=$(aws ecs list-tasks \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER" \
  --service-name "$ECS_SERVICE" \
  --desired-status RUNNING \
  --query "taskArns[0]" \
  --output text)

aws ecs stop-task \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER" \
  --task "$RUNNING_TASK_ARN" \
  --reason "docs-14 failure recovery test"
```

실패 확인:

```bash
aws ecs describe-tasks \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER" \
  --tasks "$RUNNING_TASK_ARN" \
  --query "tasks[0].{lastStatus:lastStatus,stopCode:stopCode,stoppedReason:stoppedReason,containers:containers[].reason}"
```

- ECS > Clusters > `fileshare-dev-ecs` > Services > Events에서 `docs-14 failure recovery test` reason과 새 task 기동 이벤트를 확인한다.
- ECS > Clusters > `fileshare-dev-ecs` > Tasks > Stopped에서 stopped reason과 container exit reason을 확인한다.
- CloudWatch > Log groups > `/ecs/fileshare-dev-backend`에서 중지된 task 시간대의 application log stream을 확인한다.

복구 확인:

```bash
aws ecs wait services-stable \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE"

aws ecs describe-services \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER" \
  --services "$ECS_SERVICE" \
  --query "services[0].{desired:desiredCount,running:runningCount,pending:pendingCount,deployments:deployments[].rolloutState}"

curl -s -o /dev/null -w "%{http_code}\n" "$AWS_BASE_URL/api/health"
```

정상 기준:

- `runningCount`가 `desiredCount`로 돌아온다.
- ALB target group의 target이 다시 `healthy`가 된다.
- health API가 `200`을 반환한다.
- 장애 원인은 ECS stopped task, service event, backend log stream 중 최소 한 곳에서 추적 가능하다.

### 8.2 Lambda 실패 주입과 정상 재처리

S3 이벤트 형식은 유지하되 `s3` 필드를 누락한 payload로 Lambda를 직접 호출한다. 실제 S3 object나 backend API 데이터는 변경하지 않는다.

```bash
cat > /tmp/docs14-lambda-fail.json <<'JSON'
{
  "Records": [
    {
      "eventName": "ObjectCreated:Put"
    }
  ]
}
JSON

aws lambda invoke \
  --region "$AWS_REGION" \
  --function-name "$FILE_PROCESSOR_FUNCTION" \
  --payload fileb:///tmp/docs14-lambda-fail.json \
  /tmp/docs14-lambda-fail-response.json

cat /tmp/docs14-lambda-fail-response.json
```

실패 확인:

- `aws lambda invoke` 응답에 `FunctionError`가 포함된다.
- CloudWatch > Log groups > `/aws/lambda/fileshare-dev-file-processor`에서 같은 시간대의 error stack trace를 확인한다.
- CloudWatch > Alarms > `fileshare-dev-file-processor-errors`는 평가 주기 이후 `ALARM` 또는 `INSUFFICIENT_DATA`에서 `ALARM` 전환 이력을 가질 수 있다.

CLI로 로그를 확인한다.

```bash
aws logs filter-log-events \
  --region "$AWS_REGION" \
  --log-group-name "$FILE_PROCESSOR_LOG_GROUP" \
  --filter-pattern "ERROR"
```

복구 확인은 정상 파일 업로드를 다시 수행해 Lambda가 성공 처리하는지 본다.

```bash
UPLOAD_RESPONSE=$(curl -s -X POST "$AWS_BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./day13-sample.txt" \
  -F "description=docs-14 recovery validation")

echo "$UPLOAD_RESPONSE" | jq
```

업로드 후 5~30초 안에 다음을 확인한다.

- Lambda log stream에 `processed_objects` 로그가 남는다.
- S3 object tag에 `scan-status`, `processing-status`, `metadata-size`, `metadata-content-type`, `processor`가 기록된다.
- backend health API와 업로드 API는 Lambda 실패 주입 이후에도 정상 동작한다.

### 8.3 설정 오류 분석 체크리스트

실패 주입 없이 운영 중 오류가 발생했다면 다음 순서로 원인을 좁힌다.

- 배포 이미지 오류는 ECS stopped task reason, container exit reason, `/ecs/fileshare-dev-backend` log stream, 이전 task definition revision rollback 가능 여부를 확인한다.
- S3 권한 오류는 backend log의 `AccessDenied`, IAM task role의 `arn:aws:s3:::<bucket>/files/*` 권한, 실제 object key prefix를 함께 확인한다.
- Lambda 후처리 오류는 Lambda error log, `fileshare-dev-file-processor-errors` alarm, S3 event notification prefix, Lambda execution role의 S3 prefix 권한을 함께 확인한다.

## 9. 로그 및 알람 확인

로그 그룹 확인은 7장의 정상 업로드 검증과 8장의 실패 주입 검증에서 수행한다. 이 장에서는 알람 구성이 존재하고 실패 주입 이후 상태 전환을 추적할 수 있는지만 확인한다.

```text
fileshare-dev-file-processor-errors
fileshare-dev-file-processor-duration
fileshare-dev-backend-cpu-high
fileshare-dev-backend-memory-high
fileshare-dev-alb-target-5xx
```

CLI 확인:

```bash
aws cloudwatch describe-alarms \
  --region "$AWS_REGION" \
  --alarm-names \
    fileshare-dev-file-processor-errors \
    fileshare-dev-file-processor-duration \
    fileshare-dev-backend-cpu-high \
    fileshare-dev-backend-memory-high \
    fileshare-dev-alb-target-5xx \
  --query "MetricAlarms[].{Name:AlarmName,State:StateValue,Reason:StateReason}"
```

정상 기준:

- 정상 상태에서 alarm은 `OK` 또는 `INSUFFICIENT_DATA`일 수 있다.
- 8장의 실패 주입 후 관련 alarm이 평가 주기와 임계치에 따라 `ALARM`으로 전환되거나 alarm history에 실패 지표가 남는다.
- 온프레미스의 `docker compose logs`보다 AWS 환경에서는 backend, Lambda, ALB, ECS 지표가 CloudWatch에 모여 장애 원인을 분리해 볼 수 있다.

## 10. 성능 및 확장성 점검

개발 환경에서는 비용과 용량을 고려해 짧은 smoke 성능 점검만 수행한다.

예시:

```bash
for i in $(seq 1 20); do
  curl -s -o /dev/null -w "%{http_code} %{time_total}\n" "$BASE_URL/api/health"
done
```

확인 기준:

- 모든 응답 코드가 `200`이다.
- 응답 시간이 일시적으로 튀는 요청은 CloudWatch ALB target response time과 backend log로 원인을 확인한다.
- ECS CPU/memory 지표와 Auto Scaling target tracking policy가 연결되어 있는지 확인한다.
- Lambda duration/error 지표가 backend API 지표와 분리되어 있는지 확인한다.
- 온프레미스에서는 scale out이 컨테이너 수와 Nginx upstream 조정에 의존하지만, AWS에서는 ECS desired count와 Auto Scaling policy로 조정 가능해야 한다.
- 실제 부하 테스트는 별도 테스트 계정, 비용 예산, 알람 action을 준비한 뒤 수행한다.

## 11. 완료 판정

Day 13 완료 기준:

- 온프레미스와 AWS의 핵심 API 응답 계약이 동일하다.
- 업로드/다운로드/삭제 시나리오가 두 환경에서 성공한다.
- 저장소 object key와 DB metadata 계약이 유지된다.
- 장애 원인을 로그와 알람으로 추적할 수 있다.
- AWS 환경에서 ALB, ECS, S3, Lambda, Aurora, ElastiCache, CloudWatch의 역할이 온프레미스 구성 대비 어떤 운영 문제를 줄이는지 설명할 수 있다.
- 서버리스 후처리가 backend API와 독립적으로 실행되고 관측되는지 확인할 수 있다.
- Auto Scaling과 CloudWatch 지표를 통해 성능 병목을 추적하고 확장 판단을 할 수 있다.
- 최종 검증 절차가 재실행 가능한 문서로 남아 있다.
