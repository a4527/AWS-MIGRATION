로컬 호스트 준비, 온프레미스(로컬 Docker Compose) 구축, AWS 배포 절차를 관리한다. 검증 시나리오는 [VALIDATION](VALIDATION.md), 전환 절차는 [MIGRATION](MIGRATION.md), 실제 수행 범위는 [PROGRESS](../PROGRESS.md)를 따른다. 이전 Day별 설명은 [이전 문서 요약](../LEGACY.md)에 남겼다.

## 1. 로컬 호스트 준비

온프레미스 환경은 현재 로컬 PC의 WSL2(Ubuntu)에서 Docker Compose로 운영한다([DEC-004](../DECISIONS.md#결정-목록)). 작업 ID는 ENV-01이다.

### 1.1 Docker 설치

둘 중 하나를 쓴다.

- **Docker Desktop + WSL 연동 (권장)**: Windows에 Docker Desktop을 설치하고 Settings > Resources > WSL integration에서 사용하는 배포판(Ubuntu)을 켠다. WSL 셸에서 `docker` 명령을 그대로 쓴다.
- **WSL2 안에 Docker Engine 설치**: Docker Desktop 없이 Ubuntu에 Docker Engine과 Compose 플러그인을 설치한다. [Docker 공식 Ubuntu 설치 안내](https://docs.docker.com/engine/install/ubuntu/)를 따르고, `systemd`가 켜져 있어야 한다(`/etc/wsl.conf`의 `[boot] systemd=true`).

확인:

```bash
docker version
docker compose version
docker run --rm hello-world
```

`docker` 명령을 찾을 수 없다는 메시지가 나오면 [트러블슈팅](../TROUBLESHOOTING.md#wsl2에서-docker-명령을-찾을-수-없음)을 본다.

### 1.2 도구와 자원

```bash
sudo apt-get update && sudo apt-get install -y jq curl
free -h        # WSL에 할당된 메모리. 4GB 이상 권장
```

- 메모리가 부족하면 Windows의 `%UserProfile%\.wslconfig`에 `[wsl2]` `memory=6GB`를 넣고 `wsl --shutdown` 후 다시 연다.
- 저장소는 WSL 파일시스템(`/home/<user>/...`)에 둔다. `/mnt/c` 아래에서 빌드하면 매우 느리다.
- 백엔드를 컨테이너 밖에서 빌드·테스트하려면 JDK 17이 필요하다. Compose 실행만 할 때는 필요 없다(이미지 안에서 빌드한다).

## 2. 설정 파일

```bash
cp .env.example .env
```

`.env`에서 최소 다음 값을 바꾼다. `.env`는 Git에 올리지 않는다(`.gitignore`).

| 변수 | 용도 | 기본값 |
| --- | --- | --- |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 | `fileshare` (교체) |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | MinIO 계정 | `minioadmin` (교체) |
| `APP_JWT_SECRET` | JWT 서명 키, 32바이트 이상 | 예시값 (교체) |
| `NGINX_PORT` | 외부 진입 포트 | `8080` |
| `APP_FILE_MAX_SIZE_BYTES` | 업로드 최대 크기 | `10485760` |
| `APP_FILE_IDEMPOTENCY_TTL` | 멱등성 키 보관 기간 | `PT24H` |
| `APP_FILE_IDEMPOTENCY_IN_PROGRESS_TIMEOUT` | 진행 중 요청을 중단으로 보는 시간, 고아 점검 최소 경과 시간 | `PT15M` |
| `APP_FILE_UPLOAD_RECORD_ATTEMPTS` | 메타데이터 기록 재시도 횟수 | `3` |

JWT secret 예: `openssl rand -base64 48`. 업로드 크기를 바꾸면 `nginx/conf.d/default.conf`의 `client_max_body_size`도 같이 바꾼다.

## 3. 온프레미스 구축

작업 ID는 ONP-01이다. 기동 후 확인은 [V-ONP-01](VALIDATION.md#v-onp-01-스택-기동)을 따른다.

### 3.1 구성

| 서비스 | 이미지 | 역할 | 호스트 포트 |
| --- | --- | --- | --- |
| `nginx` | nginx:1.27-alpine | 외부 진입점, `/api/`·`/actuator/health`만 backend로 전달, 본문 10MB 제한 | `8080` |
| `backend` | `backend/Dockerfile` 빌드 | Spring Boot API, 프로필 `postgres,redis,minio` | 없음(내부 8080) |
| `postgres` | postgres:16-alpine | `users`·`files`·`upload_requests` | `5432` (로컬 확인용) |
| `redis` | redis:7-alpine | 메타데이터 캐시 | `6379` (로컬 확인용) |
| `minio` | bitnamilegacy/minio:2025.4.22 | 파일 객체, 버킷 `files`. 데이터 경로 `/bitnami/minio/data`, 사용자 1001 | `9000`, 콘솔 `9001` |
| `mc` | bitnamilegacy/minio-client:2025.4.16 (`tools` 프로필) | 검증용 MinIO 클라이언트, 평소에는 실행하지 않음 | 없음 |

공식 MinIO 이미지는 배포가 중단되어 같은 버전의 Bitnami 보관 이미지를 쓴다([DEC-005](../DECISIONS.md#결정-목록)). 보안 업데이트가 없으므로 외부에 노출하지 않는다. 데이터는 볼륨 `postgres-data`, `redis-data`, `minio-data`에 저장된다. 스키마는 backend 기동 시 Hibernate `ddl-auto=update`가 만든다. 운영에 가까운 구성에서는 DB·캐시·저장소 포트 공개를 제거한다.

### 3.2 실행

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f backend      # "Started FileShareApiApplication"이 보이면 준비됨
curl http://localhost:8080/api/health
```

첫 빌드는 Gradle 의존성을 받느라 몇 분 걸린다. MinIO 버킷은 첫 업로드 때 backend가 만든다.

브라우저로 기능을 확인하려면 http://localhost:8080/test/ 를 연다. nginx가 `nginx/test-ui/index.html`을 서빙하는 수동 테스트 페이지로, 회원가입·로그인·내 정보·업로드(`Idempotency-Key` 재전송 포함)·목록·조회·다운로드·삭제·관리자 고아 객체 점검을 버튼으로 호출하고 상태 코드와 응답을 보여 준다. 백엔드에 CORS 설정이 없으므로 API와 같은 주소(8080)에서 열어야 한다. AWS(ALB)에는 배포되지 않는다. 판정 기준은 [VALIDATION](VALIDATION.md)을 따른다. 저장된 객체는 MinIO 콘솔(http://localhost:9001, `.env`의 MinIO 계정)에서 본다.

### 3.3 운영 명령

```bash
docker compose restart backend                 # backend만 재시작
docker compose up --build -d backend           # 코드 변경 반영
docker compose stop <service> / start <service> # 장애 주입 검증
docker compose down                            # 중지(데이터 유지)
docker compose down -v                         # 중지 + 볼륨 삭제(데이터 초기화)
```

### 3.4 백업

마이그레이션 전과 장애 실험 전에 받아 둔다. 산출물은 Git에 올리지 않는다.

```bash
mkdir -p backup
docker compose exec -T postgres pg_dump -U "${POSTGRES_USER:-fileshare}" -Fc "${POSTGRES_DB:-fileshare}" > backup/fileshare.dump
docker compose --profile tools run --rm -v "$PWD/backup:/backup" mc "mc mirror --overwrite local/files /backup/minio-files"
```

### 3.5 애플리케이션 단독 실행 (개발용)

```bash
cd backend
./gradlew test                                         # 자동 테스트
./gradlew bootRun                                      # 인메모리 저장소·로컬 파일 저장
SPRING_PROFILES_ACTIVE=postgres,redis,minio ./gradlew bootRun   # Compose의 postgres·redis·minio 사용
```

프로필별 연결 변수: PostgreSQL `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`, Redis `REDIS_HOST`·`REDIS_PORT`·`REDIS_SSL_ENABLED`·`REDIS_TIMEOUT`(기본 500ms), MinIO `MINIO_ENDPOINT`·`MINIO_ACCESS_KEY`·`MINIO_SECRET_KEY`·`MINIO_BUCKET`, S3 `AWS_REGION`·`S3_BUCKET`·`S3_OBJECT_PREFIX`.

AWS 실행 시 애플리케이션 환경변수 기준:

```bash
SPRING_PROFILES_ACTIVE=postgres,redis,s3
DB_URL=jdbc:postgresql://<aurora-endpoint>:5432/fileshare
DB_USERNAME=fileshare
DB_PASSWORD=<secret>
REDIS_HOST=<elasticache-primary-endpoint>
REDIS_PORT=6379
REDIS_SSL_ENABLED=true
AWS_REGION=us-east-1
S3_BUCKET=<terraform-output-file-bucket-name>
S3_OBJECT_PREFIX=files/
```

`DB_PASSWORD`·`APP_JWT_SECRET`은 일반 환경변수가 아니라 task definition `secrets`로 주입한다. 값은 SSM Parameter Store SecureString에 있고 task definition에는 parameter ARN만 있다. ECS execution role이 task 시작 때 값을 읽는다([DEC-013](../DECISIONS.md#결정-목록), [비밀값 관리](#42-1-비밀값-관리-sec-01)).

## 4. AWS 배포

AWS 리소스 생성, GitHub Actions 배포, 기능 확인, 정리 절차를 순서대로 실행하는 런북이다. PORT-01에서 계정·예산·정리 조건을 확정한 뒤에만 실행한다. 대상 구성은 현재 Terraform 그대로다([DEC-004](../DECISIONS.md#결정-목록)).

Terraform은 유지 계층(`dev-data`: VPC·S3·Aurora·ECR·GitHub Actions 배포 role)과 실습 계층(`dev`: NAT·ALB·ECS·Lambda·Redis 등)으로 나뉜다([DEC-008](../DECISIONS.md#결정-목록), [계층 설명](AWS.md#유지-계층과-실습-계층)). 유지 계층은 한 번 만들고 남겨 두며, 실습이 끝나면 실습 계층만 `terraform destroy`로 정리한다.

### 4.0 사전 준비

PORT-01에서 정한 실행 조건([DEC-006](../DECISIONS.md#결정-목록), 인증은 [DEC-014](../DECISIONS.md#결정-목록)):

| 항목 | 값 |
| --- | --- |
| 리전 | `us-east-1` (AZ `us-east-1a`, `us-east-1b`) |
| 인증 | IAM user 콘솔 자격 증명으로 `aws login`(프로필 `bootcamp`). 장기 access key는 쓰지 않는다. 계정 정리 정책이 장기 키를 지운다(2026-11-20 확인). Terraform은 `credential_process` 프로필(`bootcamp-tf`)로 같은 세션을 쓴다. 계정은 조직의 연결 계정(linked account)이라 비용 할당 태그 조회 권한이 없다 |
| 실습 데이터 | 검증 시나리오로 새로 만든 데이터만 쓴다. 온프레미스 데이터는 옮기지 않는다([DEC-007](../DECISIONS.md#결정-목록)) |
| 보존·삭제 범위 | 유지: S3 file bucket, Aurora(0 ACU 자동 일시정지), VPC. 실습마다 삭제: `dev` 계층 전체 |
| 실험 중단 조건 | 실습 종료, 예산 알림 수신, 원인 모를 비용 증가 시 `dev` 계층을 바로 destroy한다 |
| 예산 한도·알림 | 월 $10. 실제 비용 50·80·100%와 예측 100%에서 이메일로 알린다. 유지 계층 Terraform의 `aws_budgets_budget`이 만들고, 수신 주소는 `dev-data/terraform.tfvars`에만 둔다. 기본으로 `Owner` 태그 비용만 센다. Owner가 비용 할당 태그로 활성화되어 있지 않으면 예산이 0으로 보이므로 첫 apply 뒤 확인한다(4.4) |
| 필수 태그 | 모든 리소스에 `Owner`를 붙인다. 없으면 계정 정리 정책에 삭제될 수 있다. 값은 두 계층 tfvars의 `owner`에 둔다([DEC-009](../DECISIONS.md#결정-목록), [태그 규칙](AWS.md#공통-태그)) |
| 계정 ID | 비밀값은 아니지만 문서에 적지 않는다. `aws sts get-caller-identity`로 확인한다 |


로컬에 다음 도구가 필요하다.

```text
aws cli
terraform
docker
git
curl
```

GitHub repository도 먼저 준비되어 있어야 한다.

```text
https://github.com/<owner>/aws-migration-project
```

### 4.1 AWS 로그인 확인

로컬 터미널에서 AWS에 로그인한다.

`aws login` 방식([DEC-014](../DECISIONS.md#결정-목록)). IAM user에 `SignInLocalDevelopmentAccess` 권한이 필요하고, AWS CLI 2.32 이상에서 쓸 수 있다. `~/.aws/config`에 다음 두 프로필을 둔다. `bootcamp`의 `login_session`은 처음 로그인할 때 CLI가 채운다.

```ini
[profile bootcamp]
region = us-east-1

[profile bootcamp-tf]
credential_process = /home/<user>/.local/bin/aws configure export-credentials --profile bootcamp --format process
region = us-east-1
```

```bash
aws login --profile bootcamp     # WSL에서는 출력된 URL을 Windows 브라우저에서 연다
export AWS_PROFILE=bootcamp-tf   # 이 셸의 aws CLI·Terraform이 모두 이 세션을 쓴다
```

aws provider 5.x는 `login_session`을 직접 읽지 못해 `credential_process`로 감싼다. 세션이 만료되면 `aws login --profile bootcamp`만 다시 하면 된다. 개인 백업 계정(`personal`)과 동시에 로그인해 둘 수 있고, 브라우저가 다른 계정에 로그인돼 있으면 시크릿 창에서 URL을 연다.

현재 로그인된 AWS 계정을 확인한다.

```bash
aws sts get-caller-identity
```

출력의 `Account` 값이 리소스가 생성될 AWS 계정이다.

### 4.1-1 원격 state 준비 (IAC-01)

두 계층의 state는 S3 bucket 하나에 계층별 key로 둔다(`fileshare/dev-data/terraform.tfstate`, `fileshare/dev/terraform.tfstate`). 잠금은 S3 자체 잠금(`use_lockfile`)이라 state 옆에 `.tflock` 객체가 생기고, DynamoDB 테이블은 쓰지 않는다([DEC-011](../DECISIONS.md#결정-목록)). bucket은 `terraform/bootstrap`이 만들고, bootstrap 자신은 로컬 state를 쓴다. Terraform 1.11 이상이 필요하다.

state bucket이 없을 때만 한 번 실행한다.

```bash
cd terraform/bootstrap
cp terraform.tfvars.example terraform.tfvars   # owner, state_bucket_name(전역 유일) 수정
terraform init && terraform plan && terraform apply
terraform output -raw state_bucket_name
```

bucket은 버전 관리·SSE-S3 암호화·퍼블릭 차단·TLS 외 접근 거부·`prevent_destroy`가 켜져 있다. 이전 state 버전은 최신 20개를 넘는 것 중 90일 지난 것부터 만료된다. bootstrap의 로컬 state를 잃으면 `terraform import aws_s3_bucket.state <bucket>` 등으로 되살린다(리소스는 bucket과 그 설정뿐).

각 계층 디렉터리에 `backend.hcl`(gitignore 대상)을 만들고 init한다.

```bash
cd terraform/environments/dev-data      # dev도 같다
cp backend.hcl.example backend.hcl      # bucket = "<state_bucket_name>"
terraform init -backend-config=backend.hcl
```

로컬 state가 이미 있는 계층은 `terraform init -migrate-state -backend-config=backend.hcl`로 옮긴다. 옮긴 뒤 `terraform state pull`의 리소스가 원본과 같은지, `terraform plan`이 변경 없음인지 확인하고 로컬 사본을 정리한다. SEC-01 이전에 만든 로컬 사본과 원격 state의 이전 버전에는 당시 DB 비밀번호가 평문으로 있다. SEC-01 적용 때 비밀번호를 새 값으로 교체했으므로 그 값으로는 접속할 수 없다.

state 문제 대응:

| 상황 | 증상 | 대응 |
| --- | --- | --- |
| 동시 실행 | `Error acquiring the state lock`, `StatusCode: 412`, 다른 작업의 `ID`·`Operation` | 다른 작업이 끝나기를 기다린다. `-lock-timeout=60s`로 대기할 수 있다 |
| 남은 잠금 | 실행 중인 Terraform이 없는데 같은 오류가 계속됨(프로세스 강제 종료·네트워크 단절 뒤) | 정말 실행 중인 작업이 없는지 확인한 뒤 `terraform force-unlock <ID>` |
| state 손상·잘못된 덮어쓰기 | plan이 이미 있는 리소스를 새로 만들려 하거나 state를 읽지 못함 | 아래 버전 복구 |

```bash
B=<state_bucket_name>; K=fileshare/dev-data/terraform.tfstate
aws s3api list-object-versions --bucket $B --prefix $K \
  --query "Versions[?Key=='$K'].{id:VersionId,latest:IsLatest,time:LastModified}"
aws s3api get-object --bucket $B --key $K --version-id <복구할 VersionId> restore.tfstate
terraform show restore.tfstate                    # 내용 확인
terraform state push -force restore.tfstate       # serial이 낮아 -force가 필요하다
terraform plan                                    # 실제 인프라와 차이 확인
rm restore.tfstate
```

`state push`는 serial이 같고 내용이 다른 state를 `-force` 없이 거부한다. 복구한 버전 이후에 바뀐 리소스는 state에서 빠지므로 plan 결과를 보고 import나 apply로 맞춘다.

### 4.2 Terraform 변수 파일 작성

유지 계층을 처음 만들 때만 다음을 수행한다.

```bash
cd terraform/environments/dev-data
cp terraform.tfvars.example terraform.tfvars
```

`dev-data/terraform.tfvars`에서 최소 다음 값을 수정한다.

```hcl
owner               = "<Owner 태그 값>"
file_bucket_name    = "replace-with-globally-unique-bucket-name"
budget_alert_emails = ["<알림 받을 주소>"]
```

DB 비밀번호는 넣지 않는다. apply 때 Terraform이 만들어 Aurora와 SSM에 쓴다([4.2-1](#42-1-비밀값-관리-sec-01)). 기본값은 Aurora 최소 0 ACU·300초 뒤 자동 일시정지, 삭제 보호 켜짐, 최종 스냅샷 남김이다.

실습 계층 변수 파일을 만든다.

```bash
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
```

`dev/terraform.tfvars`에서 최소 다음 값을 수정한다. `file_bucket_name`은 유지 계층과 같은 값이어야 한다. DB 비밀번호와 JWT secret은 넣지 않는다.

```hcl
owner             = "same-as-dev-data"
file_bucket_name  = "same-as-dev-data"

github_repository = "<github-owner>/aws-migration-project"
github_branch     = "main"

ecs_desired_count = 1
```

`file_bucket_name`은 전 세계에서 유일해야 한다.

### 4.2-1 비밀값 관리 (SEC-01)

결정: [DEC-013](../DECISIONS.md#결정-목록). 값은 tfvars·state·plan에 없다. Terraform은 apply 중에 ephemeral `random_password`로 값을 만들고 write-only 인자(`master_password_wo`, `value_wo`)로만 보낸다. 두 값 모두 AWS 관리 키 `aws/ssm`으로 암호화한다.

| 값 | SSM parameter | 만드는 계층 | 교체 |
| --- | --- | --- | --- |
| Aurora master password | `/<project>/<environment>/aurora/master-password` | `dev-data` (Aurora와 같은 apply) | `aurora_master_password_version` 증가 |
| JWT secret | `/<project>/<environment>/app/jwt-secret` | `dev` (실습 계층과 함께 생성·삭제) | `app_jwt_secret_version` 증가 |

- write-only 값은 `*_version`이 바뀔 때만 다시 보낸다. 버전을 그대로 두면 apply를 반복해도 값이 바뀌지 않는다.
- 실습 계층은 DB 비밀번호 parameter를 data source로 읽지 않고 이름으로 ARN을 만든다. `data "aws_ssm_parameter"`는 값을 state에 저장하기 때문이다.
- ECS execution role은 이 두 parameter에만 `ssm:GetParameters`를 가진다. task role(애플리케이션)과 GitHub Actions 배포 role에는 SSM 권한이 없다.
- 실행 중인 task는 시작할 때 읽은 값을 계속 쓴다. 교체한 뒤에는 새 배포로 task를 바꾼다.

DB 비밀번호 교체:

```bash
cd terraform/environments/dev-data
terraform apply -var aurora_master_password_version=<현재+1>   # 또는 tfvars에 기록
# 실습 계층이 떠 있으면 바로 task를 교체한다. 기존 연결이 끊기면 이전 비밀번호로는 다시 연결하지 못한다.
aws ecs update-service --cluster <cluster> --service <service> --force-new-deployment
```

값이 필요하면(`pg_dump`·수동 점검) SSM에서 읽고 화면·로그·파일에 남기지 않는다.

```bash
aws ssm get-parameter --name "$(terraform output -raw aurora_master_password_parameter_name)" \
  --with-decryption --query Parameter.Value --output text
```

apply 중 SSM과 Aurora 중 한쪽만 바뀌면 값이 어긋날 수 있다. 버전을 한 번 더 올려 두 곳에 같은 새 값을 다시 쓴다.

1 task 실습 구성은 `ecs_desired_count = 1`과 `ecs_min_capacity = 1`을 함께 설정한다. 생성 후 task 수는 Auto Scaling이 관리한다.

HTTPS와 도메인 연결을 함께 검증하려면 Route53 public hosted zone을 먼저 준비하고 다음 값을 추가한다.

```hcl
domain_name              = "api.example.com"
route53_hosted_zone_name = "example.com"
```

두 값을 비워두면 Terraform은 ACM/Route53 HTTPS 구성을 건너뛰고 ALB HTTP endpoint를 사용한다.

### 4.3 Terraform 검증

유지 계층이 아직 없으면 `dev-data`에서 먼저 실행한다. 실습 계층은 유지 계층 리소스를 이름으로 조회하므로 유지 계층이 없으면 plan이 실패한다.

```bash
terraform init -backend-config=backend.hcl
terraform fmt -recursive ../..
terraform validate
terraform plan
```

`terraform plan`에서 생성될 리소스를 확인한다.

유지 계층(`dev-data`) 생성 대상:

```text
VPC / IGW / subnet / route table (private route table에는 NAT 경로 없음)
S3 bucket (versioning, 암호화, public access 차단)
Aurora PostgreSQL cluster / instance / subnet group / security group (규칙 없음)
```

실습 계층(`dev`) 생성 대상:

```text
NAT Gateway / EIP / private 기본 경로
Aurora SG의 5432 from application SG 규칙
ALB / target group
ACM certificate / Route53 records, when domain variables are set
ECS cluster / service / task definition (이미지는 유지 계층 ECR의 `latest`)
ElastiCache Redis
IAM roles / policies
Lambda file processor
S3 bucket notification
CloudWatch log groups / alarms
ECS Service Auto Scaling
```

### 4.4 Terraform apply

유지 계층이 없으면 `dev-data`에서 먼저, 그다음 `dev`에서 리소스를 생성한다.

```bash
terraform apply
```

유지 계층을 처음 apply한 뒤 다음을 확인한다.

```bash
# 예산 구독 이메일 확인 메일은 오지 않는다(Budgets는 확인 절차 없음). 예산 목록만 확인한다.
aws budgets describe-budgets --account-id "$(aws sts get-caller-identity --query Account --output text)" \
  --query 'Budgets[].{name:BudgetName,limit:BudgetLimit.Amount}'
# Owner가 활성 비용 할당 태그인지. Inactive이거나 없으면 budget_filter_by_owner_tag = false로 바꾸거나 관리자에게 활성화를 요청한다.
aws ce list-cost-allocation-tags --tag-keys Owner --query 'CostAllocationTags[].{key:TagKey,status:Status}'
# Owner 없는 리소스가 없는지(이 프로젝트 태그 기준)
aws resourcegroupstaggingapi get-resources --tag-filters Key=Project,Values=fileshare \
  --query 'ResourceTagMappingList[?!(Tags[?Key==`Owner`])].ResourceARN'
```

태깅 API 결과에는 이미 삭제한 리소스(예: 이전 실습의 subnet)가 한동안 남고, Aurora cluster는 이름 ARN과 리소스 ID ARN 두 개로 나온다. 개수는 `describe-subnets` 등 서비스 API로 대조한다.

ECR과 GitHub Actions 배포 role은 유지 계층에 있다([DEC-015](../DECISIONS.md#결정-목록)). 실습 계층을 다시 만들면 ECS가 ECR의 `latest`(main에서 마지막으로 검사를 통과한 이미지)로 바로 뜬다. 첫 apply 전에 `latest`가 없으면 task의 이미지 pull이 실패하므로, 먼저 main에서 workflow를 한 번 실행해 이미지를 올린다. Aurora가 일시정지돼 있으면 첫 task가 DB 연결 실패로 종료될 수 있으므로 apply 직전에 [DB를 깨운다](#46-github-actions-배포-실행).

완료 후 output을 확인한다.

```bash
terraform output
```

특히 다음 값을 확인한다.

```bash
terraform output backend_ecr_repository_url
terraform output ecs_cluster_name
terraform output ecs_service_name
terraform output application_alb_dns_name
terraform output application_url
terraform output github_actions_deploy_role_arn
terraform output file_processor_function_name
terraform output file_processor_log_group_name
```

이 시점에는 인프라는 생성되었지만 ECR image가 없으면 ECS task가 정상 실행되지 않을 수 있다.

### 4.5 GitHub Actions secret 등록

다음 output 값을 복사한다.

```bash
terraform output -raw github_actions_deploy_role_arn
```

GitHub repository에서 secret을 등록한다.

```text
Repository
-> Settings
-> Secrets and variables
-> Actions
-> New repository secret
```

Secret 이름:

```text
AWS_DEPLOY_ROLE_ARN
```

Secret 값(`dev-data`의 output, 유지 계층에 있어 실습 계층을 다시 만들어도 바뀌지 않는다):

```bash
cd terraform/environments/dev-data
terraform output -raw github_actions_deploy_role_arn | gh secret set AWS_DEPLOY_ROLE_ARN --repo <owner>/<repo>
```

### 4.6 GitHub Actions 배포 실행

`main`에 backend 변경이 push(PR 머지 포함)되면 자동으로 배포한다([DEC-015](../DECISIONS.md#결정-목록)). 코드 변경 없이 다시 배포하려면 Actions → Backend CI/CD → Run workflow(Branch: main)로 수동 실행한다.

Workflow가 수행하는 작업:

```text
backend test
Docker image build / Trivy scan
검증 이미지 artifact 저장 / load
AWS OIDC role assume
ECR login
검증 이미지 tag / ECR push (`<sha>`, `latest`)
ECS service 상태 확인 (ACTIVE가 아니면 배포를 건너뛰고 성공 처리)
실행 중인 task가 0개면 Aurora 깨우기
현재 ECS service task definition 조회
backend container image 교체
새 task definition revision 등록
ECS service rolling deployment
```

배포가 성공하면 ECS service가 새 task를 실행하고 ALB target group health check를 통과한 task로 트래픽을 보낸다.

**배포 전 DB 깨우기 ([DEC-010](../DECISIONS.md#결정-목록))**

backend는 기동할 때 DB에 연결하지 못하면 종료된다([V-LOC-04](VALIDATION.md#v-loc-04-db-재개-지연) 4-2). Aurora가 0 ACU로 일시정지돼 있으면 재개(15초 미만, AWS 안내)가 끝나기 전에 첫 task가 실패할 수 있다. 그래서 실행 중인 task가 없을 때(실습 계층을 새로 만든 직후 첫 배포, task를 0개로 줄였다 다시 늘릴 때)는 task가 뜨기 직전에 DB를 깨운다. 실행 중인 task가 있으면 그 연결 때문에 DB가 일시정지되지 않으므로 생략해도 된다.

DB는 database subnet에 있어 로컬에서 직접 연결할 수 없다. 대신 writer 인스턴스의 로그 목록 조회로 재개를 일으킨다. AWS는 연결 요청, RDS Data API 요청, 로그 조회·다운로드, 설정 변경을 재개 조건으로 안내한다([Aurora 0 ACU 안내](https://aws.amazon.com/blogs/database/introducing-scaling-to-0-capacity-with-amazon-aurora-serverless-v2/)). `dev-data` 디렉터리에서 실행한다.

```bash
CLUSTER=$(terraform output -raw aurora_cluster_identifier)
WRITER=$(aws rds describe-db-clusters --db-cluster-identifier "$CLUSTER" \
  --query 'DBClusters[0].DBClusterMembers[?IsClusterWriter].DBInstanceIdentifier' --output text)
aws rds describe-db-log-files --db-instance-identifier "$WRITER" --query 'length(DescribeDBLogFiles)'
sleep 30
aws rds describe-events --source-type db-instance --source-identifier "$WRITER" --duration 10 \
  --query 'Events[].[Date,Message]' --output text
```

판정: 마지막 이벤트 목록에 재개 시작·완료 이벤트(`Initiated resume for the DB instance ...`, `Successfully resumed the DB instance ...`)가 보이거나, 이미 실행 중이어서 재개 이벤트가 없다. 이벤트 문구와 재개 소요(약 12초)는 2026-11-20 SEC-01 apply 때 관찰했다([작업일지](../worklogs/2026-11-20.md#aws-적용)). 로그 조회만으로 재개되는지는 첫 실행(MIG-02)에서 확인해 여기에 고친다. 자동 일시정지는 300초 동안 연결이 없으면 다시 걸리므로, workflow를 시작할 때가 아니라 `ECS service rolling deployment` 단계에 들어갈 때 깨운다(테스트·빌드·검사가 5분을 넘을 수 있다). 깨우기를 빠뜨려 첫 task가 실패해도 그 연결 시도가 재개를 일으키므로 ECS가 띄우는 다음 task는 성공할 것으로 예상한다(미확인). 이 경우 실패 task를 작업일지에 기록한다.

### 4.7 AWS 콘솔 확인

AWS 콘솔에서 주요 리소스를 확인한다.

```text
ECR
-> Repositories
-> fileshare-dev-backend
-> Images

ECS
-> Clusters
-> fileshare-dev-ecs
-> Services
-> fileshare-dev-backend
-> Tasks

EC2
-> Target Groups
-> fileshare-dev-backend
-> Targets

EC2
-> Load Balancers
-> fileshare-dev-alb

CloudWatch
-> Log groups
-> /ecs/fileshare-dev-backend
-> /aws/lambda/fileshare-dev-file-processor

Lambda
-> Functions
-> fileshare-dev-file-processor

CloudWatch
-> Alarms
-> fileshare-dev-file-processor-errors
-> fileshare-dev-file-processor-duration
-> fileshare-dev-backend-cpu-high
-> fileshare-dev-backend-memory-high
-> fileshare-dev-alb-target-5xx
```

정상 기준:

```text
ECR image 존재
ECS service running task 존재
Target group target healthy
CloudWatch log stream 생성
Lambda function 생성
S3 bucket notification 연결
```

### 4.8 애플리케이션 URL 확인

Terraform output으로 애플리케이션 URL을 확인한다.

```bash
ALB_DNS=$(terraform output -raw application_alb_dns_name)
BASE_URL=$(terraform output -raw application_url)
echo "$ALB_DNS"
echo "$BASE_URL"
```

Health API를 호출한다.

```bash
curl "$BASE_URL/api/health"
```

정상 응답 예:

```json
{
  "success": true,
  "data": {
    "status": "UP"
  }
}
```

### 4.9 기능 테스트

테스트 파일을 준비한다.

```bash
printf "hello aws deployment\n" > sample.txt
```

회원가입:

```bash
curl -X POST "$BASE_URL/api/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "aws-user@example.com",
    "password": "password123!",
    "name": "AWS User"
  }'
```

로그인:

```bash
curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "aws-user@example.com",
    "password": "password123!"
  }'
```

응답에서 access token을 복사해 변수에 넣는다.

```bash
TOKEN="replace-with-access-token"
```

내 정보 조회:

```bash
curl "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TOKEN"
```

파일 업로드:

```bash
curl -X POST "$BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@sample.txt"
```

파일 목록 조회:

```bash
curl "$BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN"
```

응답에서 파일 ID를 확인한 뒤 변수에 넣는다.

```bash
FILE_ID="replace-with-file-id"
```

파일 다운로드:

```bash
curl -L "$BASE_URL/api/files/$FILE_ID/download" \
  -H "Authorization: Bearer $TOKEN" \
  -o downloaded-sample.txt
```

`application_url` output은 도메인/HTTPS 설정이 없으면 ALB HTTP URL이고, 설정되어 있으면 HTTPS URL이다.

파일 삭제:

```bash
curl -X DELETE "$BASE_URL/api/files/$FILE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

### 4.10 데이터 확인

AWS 콘솔에서 다음을 확인한다.

```text
S3
-> 업로드 bucket
-> object 생성 여부

RDS
-> Aurora cluster
-> connection metric

ElastiCache
-> Redis metric

CloudWatch Logs
-> backend application logs
-> file processor Lambda logs

S3
-> object properties
-> Tags
-> scan-status / processing-status / metadata-size 확인
```

DB에 직접 접속하려면 private subnet 접근 경로가 필요하다. 현재 database subnet은 public 접근을 허용하지 않으므로, 로컬에서 바로 접속하는 구조가 아니다.

file processor Lambda는 S3 object tag에 후처리 결과를 기록한다. DB의 `files.status`, `files.scan_status`를 직접 갱신하지 않으므로 API 응답의 scan status와 S3 tag는 아직 별도 확인 대상이다.

### 4.10-1 데이터 이전과 비교 검증

데이터 이전은 [전환 절차](MIGRATION.md#전환-절차)를 따르고, 비교 검증은 [AWS 검증](VALIDATION.md#8-aws-검증)과 [데이터 이전 검증](VALIDATION.md#7-데이터-이전-검증)을 따른다.

### 4.11 문제 발생 시 확인 순서

ALB health check 실패:

```text
EC2 Target Groups
-> target health reason 확인
```

ECS task 실패:

```text
ECS
-> Service events
-> stopped task reason
```

이미지 pull 실패:

```text
ECR image tag 존재 여부
ECS task execution role 권한
Private subnet NAT Gateway route
```

S3 AccessDenied:

```text
ECS task role
application_files_policy
S3 bucket name 환경변수
S3 object prefix 환경변수
실제 object key가 files/ prefix 아래에 있는지 확인
```

HTTPS/도메인 연결 실패:

```text
Route53 public hosted zone 이름 확인
ACM certificate validation status 확인
Route53 validation CNAME record 생성 여부 확인
ALB 443 listener와 certificate ARN 연결 확인
HTTP 요청이 HTTPS로 301 redirect 되는지 확인
```

Lambda 후처리 실패:

```text
Lambda
-> Monitor
-> CloudWatch Logs
-> /aws/lambda/fileshare-dev-file-processor

CloudWatch
-> Alarms
-> fileshare-dev-file-processor-errors

S3 bucket notification prefix가 실제 object key와 일치하는지 확인
Lambda execution role의 s3:GetObject, s3:PutObjectTagging 권한 확인
```

DB 연결 실패:

```text
Aurora endpoint
Application SG -> Aurora SG 5432
DB_URL 환경변수
Aurora master password
```

Redis 연결 실패:

```text
Redis endpoint
Application SG -> Redis SG 6379
REDIS_SSL_ENABLED=true
```

### 4.11-1 알람 대응 (OBS-02)

모든 알람은 유지 계층 SNS topic `<prefix>-alarms`로 알림(`ALARM`)과 복귀(`OK`)를 보낸다. 수신처는 예산 알림 주소이고, 이메일 구독은 처음 한 번 확인 링크를 눌러야 한다. 대시보드 `<prefix>-service`와 Logs Insights 저장 쿼리 `<prefix>/api-sli-by-route`·`<prefix>/request-trace`로 진단한다.

| 알람 | 조건 | 뜻하는 증상 | 진단 | 복구 | 에스컬레이션 |
| --- | --- | --- | --- | --- | --- |
| `alb-no-healthy-target` | healthy target 최소 0이 2분 | 서비스 중단. ALB가 502/503 또는 fail-open | target health reason → ECS service events → stopped task reason·exit code → backend 로그(기동 오류, DB 연결) | task 기동 실패면 원인 수정 후 재배포 또는 직전 정상 revision으로 `update-service`. DB 일시정지면 [DB 깨우기](#46-github-actions-배포-실행) | 10분 안에 복구되지 않거나 원인이 AWS 측(용량·서비스 장애)이면 실습 중단·기록 |
| `alb-elb-5xx` | ALB 자체 5xx 1건 이상/1분 | target 없음·연결 오류·timeout. task 교체 중에도 짧게 발생 | 대시보드 target health·running task, ALB 5xx 시각과 배포·task 교체 시각 대조 | 배포·task 교체 중이면 안정화 대기(V-AWS-04 실측 약 90초). 반복되면 위 항목 | 배포와 무관하게 반복되면 health check·deregistration 설정 검토(OPS-01) |
| `alb-target-5xx` | backend 5xx 1건 이상/1분 | 애플리케이션 오류 | `request-trace` 쿼리에 응답 헤더 `X-Request-Id` 넣어 요청 추적, `api-sli-by-route`로 경로 확인, `Unhandled exception` 로그 | 원인 수정 배포. 배포 직후면 직전 revision으로 롤백 | 데이터 정합성(업로드·삭제) 경로면 [관리자 고아 객체 점검](VALIDATION.md#6-관리자-정합성-점검) |
| `backend-cpu-high`·`backend-memory-high` | 평균이 scaling 목표값 초과 10분 | 부하 또는 누수. Auto Scaling이 함께 동작 | ECS 지표, running/desired, 요청량·p95 | 확장 범위 안이면 관찰. max에 도달하면 원인 확인 후 범위 조정 | 지속되면 PERF-01 조건으로 재현 |
| `file-processor-errors` | Lambda 오류 1건 이상/1분 | S3 후처리 실패(태그 누락) | Lambda 로그 `ERROR`, 대상 객체 태그 | 원인 수정 후 해당 객체를 다시 처리(같은 키로 이벤트 재전송 또는 수동 invoke) | 반복되면 업로드 API와 분리된 경로임을 확인하고 FILE-03에서 처리 |
| `file-processor-duration` | Lambda p95가 임계값 초과 10분 | 후처리 지연 | Lambda Duration·Invocations, 객체 크기 | 메모리·timeout 조정 | - |

알람은 실습 계층과 함께 만들고 지운다. topic과 구독은 유지 계층이라 다시 만들어도 구독을 다시 확인할 필요가 없다. 실습 계층을 만들 때마다 새 알람이 `INSUFFICIENT_DATA → OK`로 바뀌며 OK 이메일이 한 번씩 온다(2026-11-20 관찰). 실측 감지 시간: Lambda 오류 → ALARM 약 1분 40초, task 중지 → `alb-elb-5xx` ALARM 약 3분, OK 복귀 6~8분.

### 4.12 정리

실습이 끝나면 비용 방지를 위해 **실습 계층만** 삭제한다.

```bash
cd terraform/environments/dev
terraform destroy
```

삭제 후 `dev-data`의 `terraform plan`이 변경 없음(no changes)인지 확인한다. 실습 계층이 붙인 NAT 경로와 Aurora 허용 규칙이 함께 지워졌는지 보는 확인이다. Aurora가 몇 분 뒤 일시정지되는지는 콘솔의 cluster 상태나 `ServerlessDatabaseCapacity` 지표로 확인한다.

유지 계층까지 지울 때만 `dev-data`에서 `aurora_deletion_protection = false`를 apply한 뒤 `terraform destroy`한다. 기본값으로 최종 스냅샷 `<prefix>-aurora-final`이 남는다. 같은 이름의 스냅샷이 이미 있으면 삭제가 실패하므로 이전 스냅샷을 정리하거나 보존 여부를 먼저 정한다.

삭제가 실패할 수 있는 대표 원인:

```text
S3 bucket에 object가 남아 있음
ECR repository에 image가 남아 있음
Aurora final snapshot 설정
```

유지 계층의 bucket을 비어 있지 않은 상태로 지우려면 `dev-data`에서 다음 설정을 검토한다. 파일을 보존하려면 켜지 않는다.

```hcl
force_destroy_buckets = true
```

ECR repository는 유지 계층에 있어 실습 계층 destroy로 지워지지 않는다. 유지 계층을 지울 때는 `force_delete = true`라 남은 image도 함께 삭제된다(이미지는 다시 빌드할 수 있는 산출물). lifecycle 정책이 최근 20개만 남긴다. 운영 환경에서는 image 보존 정책을 먼저 정하고 `force_delete` 사용 여부를 별도로 검토한다.

### 4.13 비용 주의

1시간 실습도 비용이 발생한다.

비용 발생 리소스:

```text
NAT Gateway
ALB
ECS Fargate
Aurora Serverless v2
ElastiCache Redis
CloudWatch Logs
CloudWatch Alarms
Lambda invocation
S3
ECR
```

실습 권장 방식:

```text
(처음 한 번) dev-data terraform apply
dev terraform apply
GitHub Actions 배포
기능 테스트
dev terraform destroy
```

쉬는 동안 남는 비용은 유지 계층의 S3·Aurora 스토리지와 Aurora 백업이다. 2026-11-18 서드파티 가격 자료 기준 us-east-1 추정치는 다음과 같다(공식 가격·실제 청구로 확인 전):

| 항목 | 쉬는 동안 월 비용 |
| --- | --- |
| S3 (현재 수십 MB) | $0.01 미만 ($0.023/GB-월) |
| Aurora 0 ACU 일시정지 | 스토리지 $0.10/GB-월과 I/O만, 현재 규모 몇 센트 |
| 참고: Aurora 0.5 ACU 상시 | 약 $44 |
| 참고: RDS db.t4g.micro 상시 | 약 $14 |

## 5. CI/CD

CI/CD는 AWS 리소스를 새로 생성하지 않는다. Terraform으로 생성된 ECR repository(유지 계층)와 ECS service(실습 계층)를 대상으로 backend image를 빌드, 푸시, 배포한다.

GitHub Actions workflow:

```text
.github/workflows/backend-ci-cd.yml
```

CI 흐름:

```text
pull_request(main) / main push / workflow_dispatch
  -> backend test
  -> Docker image build
  -> Trivy image vulnerability scan (HIGH/CRITICAL)
```

배포 흐름([DEC-015](../DECISIONS.md#결정-목록)):

```text
main push / workflow_dispatch (main)
  -> backend test
  -> Docker image build / Trivy scan
  -> 검증 이미지 artifact 저장 / 배포 job에서 load
  -> AWS OIDC role assume
  -> ECR login
  -> 검증 이미지 tag / ECR push (<sha>, latest)
  -> ECS service가 ACTIVE가 아니면 종료(이미지만 push, 성공 처리)
  -> 실행 중 task가 0개면 Aurora 깨우기(DEC-010)
  -> 현재 ECS service의 task definition 조회
  -> backend container image만 새 image URI로 교체
  -> ECS service rolling deployment
```

필요한 GitHub secret:

```text
AWS_DEPLOY_ROLE_ARN
```

이 값은 Terraform output으로 확인한다.

```bash
cd terraform/environments/dev-data
terraform output github_actions_deploy_role_arn
```

Terraform은 GitHub Actions가 AWS에 로그인할 수 있도록 다음 리소스도 정의한다.

```text
GitHub Actions OIDC provider
GitHub Actions deploy role
ECR push policy
ECS service deploy policy
iam:PassRole policy for ECS task roles
RDS 읽기(깨우기용 DescribeDBClusters·DescribeDBLogFiles·DescribeEvents)
```

기본 배포 대상:

```text
AWS_REGION=us-east-1
ECR_REPOSITORY=fileshare-dev-backend
ECS_CLUSTER=fileshare-dev-ecs
ECS_SERVICE=fileshare-dev-backend
ECS_CONTAINER_NAME=backend
```

Workflow는 Terraform apply를 실행하지 않는다. 따라서 VPC, ECR, ECS, Aurora, Redis, S3 같은 리소스 생성은 별도 Terraform 단계에서 수행한다. PR은 테스트·이미지 빌드·취약점 검사까지만 수행하고 AWS에 접근하지 않는다. `main` push와 `main` 수동 실행은 검사를 통과하면 ECR push와 ECS 배포까지 진행한다. 실습 계층이 없으면 배포 단계만 건너뛴다. 배포 job은 `concurrency: deploy-dev`로 한 번에 하나만 실행하고, 진행 중인 배포를 취소하지 않는다. 경로 필터는 `backend/**`, 이 workflow 파일, `.trivyignore.yaml`이다. 환경별 승인·이미지 승격은 CICD-02 범위다.

### ECS 관리 책임과 배포 순서 (IAC-02)

| 대상 | 관리 주체 | 적용 범위 |
| --- | --- | --- |
| ECS cluster/service, IAM, ALB, 네트워크, task definition 템플릿 | Terraform | 최초 생성과 인프라·템플릿 변경 |
| Service의 `task_definition` | CD | 생성 후 이미지 배포·명시적 설정 배포·롤백 |
| Service의 `desired_count` | Application Auto Scaling | 생성 후 task 수 조정 |
| Scaling min/max·CPU/메모리 목표값 | Terraform | 확장 범위와 정책 변경. 범위 변경은 현재 task 수에도 영향을 줄 수 있음 |

Service의 `lifecycle.ignore_changes`는 `task_definition`과 `desired_count`에만 적용한다. 생성 시에는 선언값을 사용하고 이후 갱신에서는 외부 변경을 유지한다. [Terraform lifecycle 기준](https://developer.hashicorp.com/terraform/language/meta-arguments/lifecycle#ignore_changes)을 따른다. 템플릿의 image tag와 초기 task 수 변수를 바꿔도 기존 service에 직접 배포·스케일 변경을 수행하지 않는다.

최초 배포 순서:

1. PORT-01의 환경·예산·정리 조건을 먼저 확정한다. Terraform으로 ECR·ECS와 task definition 템플릿을 생성한다. 초기 task 수는 scaling min/max 범위 안에 둔다. 예를 들어 1 task 실습이면 `ecs_desired_count`와 `ecs_min_capacity`를 모두 1로 설정한다.
2. ECR에 초기 이미지가 없으면 task의 이미지 pull이 실패할 수 있다. 이 상태는 배포 성공으로 기록하지 않는다. Service 생성 후 workflow가 서비스의 최초 task definition을 읽을 수 있는지 확인한다.
3. OIDC role secret을 등록하고 main의 Backend CI/CD를 실행한다(main push 시 자동). 검사를 통과한 이미지를 ECR에 push하고 새 revision을 service에 배포한다. Service 안정화·ALB health와 기능 smoke 결과를 확인한다. 검사 실패 시 차단 원인을 해결한 뒤 다시 실행한다.

CPU·메모리·환경변수·role 등 task definition 설정을 바꿀 때는 Terraform apply로 템플릿을 갱신한 후 **설정 배포를 별도로 수행**한다. 현재 workflow는 실행 중인 revision을 복제하므로 workflow 재실행만으로 템플릿 변경이 전달되지 않는다.

1. Terraform과 CD의 동시 변경을 멈추고 현재 service revision·backend image URI/digest를 기록한다. 정상 revision은 롤백 대상으로 보존한다.
2. `terraform output -raw ecs_task_definition_template_arn`으로 정확한 템플릿 ARN을 얻는다. Family의 latest revision은 CD가 만든 revision일 수 있으므로 대신 사용하지 않는다.
3. 해당 ARN의 task definition을 조회해 등록 가능한 필드만 사용하고 backend image를 현재 검증된 이미지로 교체한다. 새 revision을 등록하고 설정 차이를 검토한 뒤 service에 명시적으로 배포한다. 비밀값은 `secrets`의 SSM ARN으로만 들어 있지만, 내부 endpoint 등 구성 정보가 있으므로 JSON 원문을 artifact·문서에 남기지 않는다.
4. 안정화·health·기능 smoke를 확인하고 실패하면 보존한 정상 revision으로 service를 되돌린다. 이 수동 설정 배포·롤백은 CICD-02/03의 자동화·실행 검증 완료를 뜻하지 않는다.

실습 계층(`dev`)을 destroy했다가 다시 apply하면 ECS service가 새로 만들어지므로([DEC-008](../DECISIONS.md#결정-목록)) 매번 위 최초 배포 순서를 따른다. ECR은 유지 계층에 있어 이미지가 남는다([DEC-015](../DECISIONS.md#결정-목록)). 이전 실습의 revision·task 수는 이어지지 않는다.

Service를 삭제·재생성하면 ignore 규칙에도 불구하고 Terraform 템플릿과 초기 task 수가 다시 사용된다. 교체 plan은 현재 배포 이미지·설정·task 수와 비교하고 복구용 이미지 존재를 확인한 뒤 실행한다. 인프라 설정이 현재 task와 호환되지 않는 변경은 템플릿 생성만으로 완료 처리하지 않는다.

AWS 실행 확인은 PORT-01 이후 다음 순서로 수행하고 실제 revision·task 수·plan 판정·정리 결과를 작업일지에 남긴다.

1. CD 배포 후 service revision을 기록하고 `terraform plan`에서 이전 revision으로 되돌리는 변경이 없는지 확인한다.
2. Auto Scaling으로 task 수가 초기값과 달라진 뒤 `terraform plan`에서 task 수를 되돌리는 변경이 없는지 확인한다. 수동 count 변경을 사용한 경우 Auto Scaling 실험과 구분한다.
3. 다른 변경이 없는 검토한 plan을 apply하고 revision·task 수를 다시 비교한다. Scaling의 동시 동작이 있으면 이벤트와 함께 판정한다.
4. 별도 실험에서 태그 또는 scaling 정책 변경은 plan에 계속 나타나는지 확인한다. Task 설정 변경은 위 명시적 설정 배포 절차로 전달되는지 확인한다.

로컬 합성 state에 대한 `plan -refresh=false` 검사는 lifecycle 동작만 확인한다. 실제 CD·Auto Scaling·AWS apply의 증빙을 대체하지 않는다.

### PR·이미지 검증과 차단 기준 (CICD-01)

- `test` → `image` → `deploy` 순서다. 테스트·이미지 빌드·검사 실패 시 뒤 job을 실행하지 않는다. 검사 도구나 DB 다운로드 실패도 통과로 처리하지 않는다.
- Trivy는 OS와 애플리케이션 패키지의 HIGH/CRITICAL 취약점을 수정 버전 유무와 관계없이 차단한다. `exit-code: 1`, `ignore-unfixed: false`를 사용하며 자동 예외·실패 무시는 두지 않는다. LOW/MEDIUM은 이 차단 범위 밖이다. 도구 설정은 [Trivy 공식 안내](https://github.com/aquasecurity/trivy-action#usage)를 따른다.
- 예외가 필요하면 해당 취약점의 영향·근거·보완책·담당자·만료일을 PR에 제시하고 명시적 검토를 거친다. 허용한 예외는 저장소 루트 `.trivyignore.yaml`에 CVE별 사유·만료일(`expired_at`)과 함께 둔다. 만료일이 지나면 Trivy가 다시 차단한다. 현재 예외: CVE-2026-47884·CVE-2026-47890(spring-webmvc 6.2.19, 수정은 Spring Framework 7/Spring Boot 4에만 있음, 앱이 XsltView·SSE·view fragment를 쓰지 않음, 2026-11-20 승인, 만료 2026-12-31, 해제 작업 SEC-02). 검사 실패를 우회하는 수동 배포 경로도 추가하지 않는다.
- PR은 `pull_request`로 실행한다. 기본 권한은 `contents: read`이며 AWS secret을 참조하지 않는다. `id-token: write`와 AWS role 참조는 배포 job에만 둔다. [GitHub job 권한 기준](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idpermissions)을 따른다.
- `main` push·수동 실행은 검사한 Docker 이미지를 1일 보존 artifact로 넘겨 배포 job에서 다시 빌드하지 않는다. 환경 간 digest 승격·승인은 CICD-02에서 검증한다.
- 병합 차단을 보장하려면 저장소 ruleset/branch protection에서 `Test backend`, `Build and scan backend image`를 필수 검사로 등록해야 한다. 경로 필터로 workflow가 생략되는 문서 전용 PR의 처리도 함께 검토한다. 이번 로컬 수정으로 원격 저장소 설정을 바꾸지는 않았다.

실행 확인은 정상 PR과 테스트 실패·이미지 빌드 실패·HIGH/CRITICAL 검출 PR에서 각각 수행한다. 실패 시 `deploy`가 실행되지 않는지, PR job에 OIDC·AWS secret이 전달되지 않는지 확인하고 실제 run 링크를 작업일지에 남긴다. 정적 workflow 검사는 이 실행 증빙을 대신하지 않는다.

ECS rolling deployment는 새 task definition revision을 만들고 service가 새 task를 띄운 뒤, ALB target group health check를 통과한 task로 트래픽을 전환한다.

Lambda 리소스와 S3 이벤트 연결은 Terraform에서 생성한다. Lambda 코드 패키징도 Terraform `archive` provider가 수행한다. 별도 Lambda CI/CD workflow는 아직 만들지 않았으며, 운영 전환 시 애플리케이션 배포와 독립적인 서버리스 배포 job으로 분리한다.

HTTPS는 선택형으로 구성한다. `terraform.tfvars`에 `domain_name`과 `route53_hosted_zone_name`을 모두 넣으면 Terraform이 ACM 인증서, DNS validation record, ALB HTTPS listener, HTTP to HTTPS redirect, Route53 ALB alias record를 구성한다. 두 값이 비어 있으면 기존 HTTP ALB endpoint를 사용한다.

## 6. 서버리스 배포 기준

- Lambda는 S3 이벤트와 연결되어야 한다.
- Lambda 실행 권한은 최소 권한 원칙으로 분리한다.
- 서버리스 로그는 CloudWatch에서 확인 가능해야 한다.
- 서버리스 배포는 애플리케이션 배포와 독립적으로 롤백 가능해야 한다.
- 현재 file processor Lambda는 object metadata를 읽고 S3 object tag에 후처리 결과를 기록한다.
- 실제 백신 엔진과 DB 상태 동기화는 후속 운영 보강 범위로 둔다.
- 애플리케이션과 Lambda의 S3 IAM 권한은 업로드 object prefix 범위로 제한한다.

기본 후처리 tag:

```text
scan-status
processing-status
metadata-size
metadata-content-type
processor
```
