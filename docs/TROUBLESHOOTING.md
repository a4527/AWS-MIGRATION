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

## WSL2에서 docker 명령을 찾을 수 없음

- 증상: `The command 'docker' could not be found in this WSL 2 distro.` 또는 `docker: command not found`.
- 원인: Docker Desktop의 WSL 연동이 꺼져 있거나 WSL 안에 Docker Engine이 없다.
- 해결 방법:
  - Docker Desktop을 쓰면 Settings > Resources > WSL integration에서 사용하는 배포판을 켜고 Docker Desktop을 다시 시작한 뒤 새 WSL 셸을 연다.
  - Docker Desktop 없이 쓰면 [로컬 호스트 준비](design/DEPLOYMENT.md#11-docker-설치)대로 Docker Engine을 설치하고 `sudo usermod -aG docker $USER` 후 다시 로그인한다.
  - `docker version`의 Server 항목이 보여야 데몬과 연결된 것이다.

## MinIO 이미지를 받지 못함

- 증상: `pull access denied for minio/minio, repository does not exist`.
- 원인: MinIO가 공식 컨테이너 이미지 배포를 중단했다(2026-11-17 확인, `quay.io/minio`도 같음).
- 해결 방법: `docker-compose.yml`의 `bitnamilegacy/minio:2025.4.22`와 `bitnamilegacy/minio-client:2025.4.16`을 쓴다([DEC-005](DECISIONS.md#결정-목록)). 공식 이미지용 `command: server /data`와 `/data` 볼륨 경로는 Bitnami 이미지에서 쓰지 않는다(데이터 경로 `/bitnami/minio/data`).
- 재발 방지 체크리스트: 보관 이미지는 보안 업데이트가 없다. 외부 노출 환경에 쓰지 않는다.

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

## ECS task가 기동 중 DB 연결 실패로 종료됨

- 증상: 새 task가 health check 전에 STOPPED가 되고, 로그에 `The connection attempt failed`(SQLState 08001)와 `Application run failed`가 있다.
- 원인: backend는 기동할 때 DB에 연결하지 못하면 재시도 없이 종료된다. Aurora가 0 ACU로 일시정지돼 있으면 재개가 끝나기 전에 연결이 실패할 수 있다(로컬 재현: [V-LOC-04](design/VALIDATION.md#v-loc-04-db-재개-지연) 4-2).
- 해결 방법:
  - [배포 전 DB 깨우기](design/DEPLOYMENT.md#46-github-actions-배포-실행)를 실행하고 재개 이벤트를 확인한다.
  - 실패한 연결 시도도 재개를 일으키므로 ECS가 띄우는 다음 task는 성공할 것으로 예상한다(MIG-02 전까지 미확인). 계속 실패하면 DB 엔드포인트·SG 5432 규칙·비밀번호를 확인한다.
- 재발 방지 체크리스트:
  - 실행 중인 task가 없을 때 배포·task 수 증가 전에 DB를 깨운다([DEC-010](DECISIONS.md#결정-목록)).

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

## 삭제한 파일의 메타데이터가 잠시 보임

- 증상: 삭제 후 `GET /api/files/{id}`가 몇 분 동안 삭제 전 정보를 돌려준다. 다운로드는 404다.
- 원인: 삭제 시 캐시 반영이 즉시·백그라운드 재시도까지 실패했다. 로그에 `Cache update failed permanently`가 있고 `file.cache.update.failures` 지표가 늘어난다.
- 해결 방법: Redis 연결을 복구한다. 값은 TTL(5분) 뒤 사라지며, 급하면 `redis-cli DEL "files:metadata:${FILE_ID}"`로 지운다.
- 재발 방지 체크리스트: 지표에 알람을 연결한다(OBS-02). DB가 원본이므로 다운로드·삭제 판단에는 영향이 없다.

## 업로드 실패 후 저장소에 객체가 남음

- 증상: `FILE_UPLOAD_FAILED` 뒤 MinIO/S3에 메타데이터 없는 객체가 있다.
- 원인: DB 기록 여부를 확인하지 못했거나(`Upload outcome unknown`), 보상 삭제가 실패했거나(`Compensating object delete failed`), 처리 도중 프로세스가 종료됐다.
- 해결 방법: 관리자 토큰으로 `GET /api/admin/storage/orphans`를 호출해 목록을 확인한 뒤 `POST /api/admin/storage/orphans/cleanup`으로 지운다. 기본 60분보다 오래된 객체만 대상이다.
- 재발 방지 체크리스트: 위 로그 문구를 알람 대상으로 둔다. 점검은 저장소 전체를 나열하므로 객체가 많으면 실행 시간을 확인한다.

## 업로드가 409 IDEMPOTENCY_IN_PROGRESS를 반환함

- 증상: 같은 `Idempotency-Key`로 다시 보낸 업로드가 409와 `Retry-After: 5`를 받는다.
- 원인: 같은 키의 이전 요청이 아직 처리 중이다. 이전 요청이 중단됐다면 처리 중 판단 기준(기본 15분)이 지날 때까지 409가 계속된다.
- 해결 방법: `Retry-After` 뒤에 같은 키로 다시 보낸다. 15분이 지나면 새 요청이 이어받는다. 상태는 `upload_requests`의 `status`, `updated_at`으로 확인한다.

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

- 증상: 업로드가 `FILE_TOO_LARGE` 또는 `FILE_INVALID_TYPE`으로 실패하거나, nginx가 `413 Request Entity Too Large`를 돌려준다.
- 원인: 기본 업로드 제한은 10MiB이고 허용 확장자는 `pdf,png,jpg,jpeg,txt,csv`다.
- 해결 방법:
  - 파일 크기와 확장자를 확인한다.
  - 필요하면 `.env` 또는 Compose 환경변수의 `APP_FILE_MAX_SIZE_BYTES`, `APP_FILE_ALLOWED_EXTENSIONS`를 조정한다.
  - Nginx `client_max_body_size`도 Spring 업로드 제한과 맞춘다.
  - `APP_FILE_MAX_SIZE_BYTES`는 Spring multipart 한도(`spring.servlet.multipart.max-file-size`)에도 쓰인다. 요청 전체 한도는 `APP_MULTIPART_MAX_REQUEST_BYTES`(기본 11MiB)다. 2026-11-17 이전 코드는 multipart 한도가 Spring 기본값 1MB여서 1MB를 넘는 업로드가 500으로 실패했다.
- 한도를 넘는 업로드에 응답 대신 `Broken pipe`·`Error writing request body to server`·연결 끊김이 오는 경우: Tomcat이 오류 응답 뒤 남은 본문을 `server.tomcat.max-swallow-size`(기본 2MB)보다 많이 읽어야 하면 연결을 닫는다. 2026-11-20 Spring Boot 3.5 업그레이드 뒤 `UploadSizeLimitIntegrationTests`(10.5MB 업로드)에서 재현했다. 클라이언트(JDK·Apache HttpClient 5)와 무관했다. `max-swallow-size`를 multipart 요청 한도(`APP_MULTIPART_MAX_REQUEST_BYTES`)로 맞춰 해결했다. 무제한(-1)은 큰 본문을 끝까지 읽으므로 쓰지 않는다([작업일지](worklogs/2026-11-20.md#cicd-자동-배포와-trivy-수정)).
- 재발 방지 체크리스트:
  - 운영 변경 시 Spring 설정과 Nginx 설정의 업로드 제한을 함께 조정한다.
  - Spring Boot·Tomcat 버전을 올리면 `UploadSizeLimitIntegrationTests`로 한도 초과 응답을 확인한다.

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
- 재검증 방법·결과와 한계 (실제 실행한 경우 작업일지 연결)
- 재발 방지 체크리스트

<a id="aws-전환-이후-확장-예정-이슈"></a>

## AWS 진단 연결

ECS 배포·ALB health·HTTPS·S3 권한·Aurora/ElastiCache 연결의 초기 확인 순서는 [AWS 런북의 문제 확인 절](design/DEPLOYMENT.md#411-문제-발생-시-확인-순서)을 따른다. 실제로 재사용할 해결 사례가 생기면 이 문서에 증상·원인·해결·재검증 근거를 추가한다.

## AWS 명령이 InvalidClientTokenId로 실패함

- 증상: `terraform plan`이나 `aws sts get-caller-identity`가 `403 InvalidClientTokenId: The security token included in the request is invalid`로 실패한다.
- 원인: 설정된 access key가 더 이상 유효하지 않다(비활성화·삭제·교체). 2026-11-19에는 전날 성공한 장기 키가 거부됐고 원인을 확인하지 못했다. 2026-11-20에 다시 거부됐을 때는 `aws sts get-access-key-info`로 부트캠프 계정의 키임을 확인했고, `iam list-access-keys` 결과 user에 키가 하나도 없었다. 키가 삭제된 것이다. 계정 정리 정책이 지운 것으로 추정한다. 세션 토큰이 있는 임시 자격 증명이면 만료(`ExpiredToken`)일 수도 있다.
- 해결 방법:
  - `env | grep ^AWS_`로 파일 설정을 덮어쓰는 환경변수가 없는지 본다.
  - 장기 키 대신 `aws login`으로 바꾼다([DEC-014](DECISIONS.md#결정-목록), [로그인 절차](design/DEPLOYMENT.md#41-aws-로그인-확인)). `~/.aws/credentials`에 남은 키는 지운다. 남겨 두면 프로필을 지정하지 않은 명령이 계속 이 키로 실패한다.
- 재검증 방법·결과: `aws sts get-caller-identity` 성공 뒤 `terraform plan`이 진행됨([2026-11-19](worklogs/2026-11-19.md#유지-계층-apply-port-01), [2026-11-20](worklogs/2026-11-20.md#aws-적용)).
- 재발 방지 체크리스트:
  - AWS 작업 시작 전에 `aws sts get-caller-identity`부터 확인한다.

## AssumeRole이 정책이 맞아 보이는데도 AccessDenied

- 증상: `aws sts assume-role`이나 `role_arn` 프로필 사용 시 `User: ...:user/<user> is not authorized to perform: sts:AssumeRole on resource: ...:role/<role>`. MFA 코드를 넣어도, 넣지 않아도 같다.
- 원인: 2026-11-19에는 실제 role 이름(`fileshare-backup-bucket-admin`)과 user inline policy의 `Resource`·로컬 `role_arn`(`fileshare-backup-admin-role`)이 달랐다. 콘솔 화면의 inline policy 이름을 role 이름으로 착각했다. 존재하지 않는 role도 같은 `AccessDenied`로 응답하고, 오류 문구는 user 정책과 trust policy 중 어느 쪽이 막았는지 알려 주지 않는다. 처음에는 `aws login` 세션의 MFA 문제로 잘못 진단해 access key로 바꿨다가 되돌렸다.
- 해결 방법:
  - IAM → Roles 목록에서 role 이름을 직접 복사해 user 정책 `Resource`, trust policy를 둔 role, 로컬 `role_arn`이 같은 ARN인지 대조한다.
  - 그래도 거부되면 trust policy의 `Condition`을 잠시 빼고 시험해 user 정책과 trust policy 중 어느 쪽인지 가른다. IAM Policy Simulator는 user 쪽 정책만 평가한다.
  - MFA 코드가 틀리면 `AccessDenied`가 아니라 MFA 검증 오류가 난다.
- 재검증 방법·결과: role 이름을 맞춘 뒤 `aws login` 세션 + `mfa_serial`로 `assumed-role/fileshare-backup-bucket-admin/...` 확인([작업일지](worklogs/2026-11-19.md#개인-계정-백업-준비)).
- 재발 방지 체크리스트:
  - 정책 이름과 role 이름을 다르게 짓는다(정책은 `...-policy`, role은 `...-role` 등).
  - 원인을 하나로 단정하기 전에 두 정책을 갈라 시험한다.

## MFA 코드 입력이 바로 실패함 (`aws: [ERROR]:`)

- 증상: Claude Code 프롬프트의 `!` 명령이나 입력이 없는 셸에서 `Enter MFA code for ...:` 뒤 `GetPassWarning: Can not control echo on the terminal`과 빈 `aws: [ERROR]:`가 나온다.
- 원인: 대화형 터미널이 아니라 MFA 코드를 입력받을 수 없다. Terraform도 MFA 입력 창을 띄우지 못한다.
- 해결 방법: 별도 WSL 터미널에서 `aws sts get-caller-identity --profile personal-backup`을 실행해 코드를 넣는다. role 자격 증명이 `~/.aws/cli/cache`에 1시간 저장되어 이후 CLI와 `credential_process` 프로필(`personal-backup-tf`)이 그 캐시를 쓴다.
- 재검증 방법·결과: 별도 터미널에서 코드 입력 후 `personal-backup-tf`로도 같은 role ARN 확인.

## role을 다시 맡을 때 MFA를 묻지 않음

- 증상: 자격 증명 방식을 바꿨는데(예: access key → `aws login`) `personal-backup` 프로필이 MFA 없이 바로 동작한다.
- 원인: 이전에 맡은 role 자격 증명이 `~/.aws/cli/cache/<hash>.json`에 만료 전까지 남아 있다. 원본 자격 증명을 바꿔도 캐시를 먼저 쓴다.
- 해결 방법: 캐시 파일의 `AssumedRoleUser.Arn`만 확인해(`jq -r .AssumedRoleUser.Arn`) 해당 role의 파일을 지운다. 같은 디렉터리의 `session.db`는 CLI 세션 ID 기록이라 자격 증명이 아니다.
- 재검증 방법·결과: 캐시 삭제 후 입력 없이 실행하면 MFA 입력 단계에서 실패하고, 별도 터미널에서 코드를 넣어야 성공함을 확인.

## WSL에서 aws login 브라우저가 열리지 않음

- 증상: `aws login`이 `xdg-open: no method available`을 출력하고 대기한다.
- 원인: WSL에 Linux 브라우저가 없다.
- 해결 방법: 출력된 `https://us-east-1.signin.aws.amazon.com/v1/authorize?...` 주소를 Windows 브라우저에서 연다. 로그인 뒤 `127.0.0.1:<port>`로 돌아오는 요청은 WSL2 localhost 전달로 대기 중인 명령이 받는다. 안 되면 `aws login --remote`를 쓴다.
- 재검증 방법·결과: 2026-11-19 두 번 모두 Windows 브라우저로 로그인 완료, `get-caller-identity --profile personal` 성공.

## 정기 drift 검사가 유지 계층에서 매일 실패함

- 증상: Terraform workflow의 `drift (dev-data)`가 2026-11-21, 11-22에 `Drift detected in dev-data`로 실패했다. 유지 계층 코드와 리소스는 바뀌지 않았다.
- 원인: 검사가 `plan -refresh-only`(state와 실제 비교)였다. 실습 계층은 유지 계층의 Aurora 보안 그룹에 ingress 규칙(`aws_security_group_rule`)을, private route table에 NAT 경로(`aws_route`)를 별도 리소스로 더한다. 유지 계층 state는 마지막 refresh 때 본 inline `ingress`·`route` 값을 기억하므로, 실습 계층을 올리거나 내릴 때마다 이 값이 "밖에서 바뀜"으로 나온다. 유지 계층 코드는 이 속성을 선언하지 않아 일반 plan은 변경이 없다.
- 해결 방법: drift 검사를 일반 `plan -detailed-exitcode`(코드와 실제 비교)로 바꿨다. 코드가 관리하는 속성이 바뀌면 여전히 적용할 변경으로 잡히고, 다른 계층이 관리하는 규칙은 무시된다. 바뀐 리소스 목록은 실행 요약과 job 로그에 함께 남긴다.
- 재검증 방법·결과: 2026-11-23 로컬에서 `-refresh-only`는 exit 2(보안 그룹 ingress 1개, route 1개 제거로 표시), 일반 plan은 exit 0. PR #8 머지 후 workflow 수동 실행에서 `drift (dev-data)`·`drift (dev)` 모두 통과(실습 계층 없음 상태).

## drift 검사가 별도 리소스로 붙인 보안 그룹 규칙을 잡지 못함

- 증상: 콘솔에서 Aurora 보안 그룹(`fileshare-dev-aurora`)에 ingress 규칙을 추가해도 정기 drift 검사가 통과한다. route table에 경로를 추가해도 마찬가지다.
- 원인: 일반 `plan`은 코드에 값을 적은 속성만 비교한다. 유지 계층 코드는 보안 그룹의 `name`·`description`·`vpc_id`·`tags`만 적고, `ingress`는 실습 계층이 `aws_security_group_rule`이라는 별도 리소스로 붙인다(`terraform/modules/aurora/main.tf`). route table의 `route`도 실습 계층 `aws_route`가 관리한다(`terraform/modules/nat/main.tf`). 그래서 유지 계층 plan에서는 ingress·route가 관리 대상이 아니다. 실습 계층 plan은 자기가 만든 규칙만 확인한다. 결과적으로 누구도 소유하지 않은 규칙은 어느 계층에서도 drift로 잡히지 않는다.
  - `-refresh-only`로 되돌리면 이 변경도 보고되지만, 실습 계층을 올리고 내릴 때마다 오탐이 난다([정기 drift 검사가 유지 계층에서 매일 실패함](#정기-drift-검사가-유지-계층에서-매일-실패함)).
  - 보안 그룹에 inline `ingress`를 적어 유지 계층이 소유하게 하면, 별도 리소스 규칙과 충돌해 두 계층이 서로 규칙을 지우게 된다.
- 해결 방법: Terraform drift 검사로는 메우지 않는다. 대신 API 호출 자체를 감시한다. CloudTrail 관리 이벤트(`AuthorizeSecurityGroupIngress`, `RevokeSecurityGroupIngress`, `CreateRoute`, `ReplaceRoute` 등)를 EventBridge 규칙으로 받아, Terraform CI·배포 role이 아닌 주체의 호출이면 SNS로 알린다. 후속 작업 [SEC-03](TASKS.md)으로 등록했다.
- 임시 확인: 그전까지는 CloudTrail 이벤트 기록에서 이벤트 이름 `AuthorizeSecurityGroupIngress`로 필터해 호출 주체와 시각을 확인한다.
- 재검증 방법·결과: SEC-03 구현 후 콘솔에서 임시 규칙을 추가해 알림 수신을 확인하고 규칙을 지운다. 아직 미수행.
