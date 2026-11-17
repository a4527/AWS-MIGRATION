구성요소의 책임·경계·데이터 흐름과 상세 설계의 진입점이다. 사용자 요구는 [요구사항](PRD.md), 현재 구현·검증 범위는 [진행 요약](PROGRESS.md), 선택 이유는 [결정 기록](DECISIONS.md)에서 관리한다. 이전 Day별 설계 기준은 [이전 문서 요약](LEGACY.md)에 남겼다. 패키지·계층·의존성 그림은 [아키텍처 페이지](assets/architecture-overview.html)를 참고한다.

## 애플리케이션 구조

Spring Boot 단일 애플리케이션이며 도메인별 패키지 안을 계층으로 나눈다. 저장소·캐시·DB는 포트(인터페이스) 뒤에 두고 프로필로 구현체를 고른다.

| 패키지 | 책임 |
| --- | --- |
| `auth` | 회원가입·로그인, JWT 발급. 사용자 저장은 `user` 저장소를 쓴다 |
| `user` | 사용자 조회·수정, 역할(`USER`, `ADMIN`) |
| `file` | 업로드(`FileUploadService`), 조회·다운로드·삭제(`FileService`), 정합성 점검, 업로드 요청 기록 |
| `infra.storage` | `FileStorage` 포트와 Local·MinIO·S3 adapter |
| `config.security` | JWT 필터, 인증 실패 응답, `AuthUserPrincipal` |
| `common` | 공통 응답·오류 코드·예외 처리, 요청 사용자 값 `Actor`, `Clock` |

| 포트 | 구현체 선택 |
| --- | --- |
| `FileMetadataRepository`, `UploadRequestRepository`, `UserRepository` | `postgres` 프로필이면 JPA, 아니면 인메모리 |
| `FileMetadataCache` | `redis` 프로필이면 Redis, 아니면 동작 없음 |
| `FileStorage` | `app.storage.type`이 `local`(기본)·`minio`·`s3` |

계층 규칙: 서비스는 API DTO와 보안 프레임워크 타입을 모른다(LAYER-01/02). 도메인 모델과 JPA Entity는 분리되어 adapter 안에서만 변환한다.

## 온프레미스 아키텍처

```text
Client → nginx(:8080) → backend(Spring Boot :8080, postgres,redis,minio)
                              ├→ postgres  (users, files, upload_requests)
                              ├→ redis     (files:metadata:{id})
                              └→ minio     (bucket files)
```

로컬 PC의 WSL2에서 Docker Compose로 실행한다. 외부 진입점은 nginx 하나이고, backend는 Compose 내부 네트워크에서 서비스 이름으로 나머지에 접근한다. 구성·포트·볼륨은 [온프레미스 구축](design/DEPLOYMENT.md#3-온프레미스-구축)을 따른다.

## AWS 아키텍처

```text
Client → Route53·ACM(선택) → ALB (public subnet)
       → ECS Fargate backend (private subnet, postgres,redis,s3)
            ├→ Aurora PostgreSQL   (database subnet)
            ├→ ElastiCache Redis   (database subnet, TLS)
            └→ S3 (files/ 접두어) → ObjectCreated → Lambda → object tag, CloudWatch
GitHub Actions(OIDC) → ECR → ECS rolling deployment
```

- VPC `10.20.0.0/16`, 2개 AZ에 public·private·database subnet. NAT Gateway는 dev 비용 절감을 위해 1개다. database subnet에는 인터넷 라우트가 없다.
- Security Group: ALB → app 8080, app → Aurora 5432, app → Redis 6379만 허용한다.
- 애플리케이션과 Lambda의 S3 권한은 `files/` 접두어로 제한한다.
- 리소스별 상세와 한계는 [AWS](design/AWS.md), 전환 대응은 [MIGRATION](design/MIGRATION.md#구성-대응)을 따른다.

## 데이터 흐름

### 인증 흐름

1. Client가 로그인 요청을 보낸다.
2. 진입점(nginx 또는 ALB)이 요청을 backend로 전달한다.
3. backend가 DB에서 사용자를 조회하고 BCrypt로 비밀번호를 확인한다.
4. 인증 성공 시 사용자 ID·이메일·역할을 담은 JWT를 발급한다.
5. Client는 이후 요청에 `Authorization: Bearer` 토큰을 붙인다. 세션은 저장하지 않는다.

### 파일 업로드 흐름

1. Client가 `multipart/form-data` 요청으로 파일을 전송한다. 재시도에 대비해 `Idempotency-Key` 헤더를 붙일 수 있다.
2. Nginx가 업로드 요청을 Spring Boot API로 전달한다.
3. Spring Boot가 파일 크기, 확장자, 권한을 검증한다.
4. 저장소 adapter가 객체 위치(UUID)를 먼저 정한다. 쓰기 응답이 유실되어도 위치를 알기 위해서다.
5. 키가 있으면 `upload_requests`에 `IN_PROGRESS`와 객체 위치를 기록한다. 같은 키의 완료된 요청은 첫 결과를 돌려주고, 진행 중이면 409, 다른 요청이면 422로 끝낸다.
6. 파일 바이너리를 크기를 아는 스트림으로 MinIO/S3에 저장한다. 전체 파일을 메모리에 올리지 않는다.
7. 메타데이터를 PostgreSQL에 저장하고, 키가 있으면 같은 트랜잭션에서 요청을 `COMPLETED`로 바꾼다. 실패하면 짧게 재시도한다.
8. Redis에 캐시를 반영한다. 실패해도 업로드는 성공이다.

실패 처리([DEC-002](DECISIONS.md#결정-목록)):

| 상황 | 처리 |
| --- | --- |
| 객체 쓰기 실패 또는 응답 유실 | 정해 둔 위치의 객체를 지우고 `FILE_UPLOAD_FAILED` |
| 메타데이터 기록이 끝내 실패하고, DB 조회로 기록 없음이 확인됨 | 객체를 지우고 요청을 `FAILED`로 바꾼 뒤 `FILE_UPLOAD_FAILED` |
| 저장 오류가 났지만 실제로는 커밋됨 | 저장된 메타데이터로 성공 응답 |
| 기록 여부를 확인할 수 없음 (DB 연결 불가) | 객체를 남기고 `FILE_UPLOAD_FAILED`. 요청은 `IN_PROGRESS`로 남아 15분 뒤 새 시도가 이어받을 수 있다. |
| 보상 삭제 실패 또는 처리 도중 프로세스 종료 | 객체가 남는다. 관리자 정합성 점검으로 찾는다. |

### 저장소 정합성 점검

관리자 API(`/api/admin/storage/orphans`)가 저장소 객체를 나열해 어떤 `files` 행(논리 삭제 포함)도 가리키지 않는 객체를 보고하고, 요청 시 지운다. 진행 중인 업로드를 건드리지 않도록 기본 60분, 최소 15분보다 오래된 객체만 대상으로 하며 삭제 직전에 다시 확인한다. 저장소 전체 목록을 한 번에 읽으므로 객체 수가 많아지면 배치·페이지 단위 처리가 필요하다.

### 파일 다운로드 흐름

1. Client가 파일 다운로드를 요청한다.
2. Spring Boot가 DB에서 권한과 파일 상태를 확인한다. 캐시는 쓰지 않는다.
3. Spring Boot가 MinIO에서 바이너리를 조회한다.
4. 파일 스트림을 Client로 반환한다.

### 파일 삭제 흐름

1. Client가 삭제 요청을 보낸다.
2. Spring Boot가 DB에서 권한을 확인한다.
3. 파일 상태를 `deleted`로 변경한다.
4. 메타데이터는 즉시 논리 삭제로 반영한다.
5. 캐시에는 삭제 상태를 덮어쓴다. 실패해도 삭제는 성공으로 응답한다.
6. 실제 바이너리 정리는 후속 운영 절차로 분리할 수 있다.

### 캐시 일관성

[DEC-003](DECISIONS.md#결정-목록)을 따른다.

- 캐시는 메타데이터 단건 조회에만 쓴다. 다운로드·삭제·목록은 DB를 읽는다.
- Redis 읽기·쓰기 오류는 로그만 남기고 DB로 우회한다. Redis가 멈춰도 파일 API는 동작한다.
- 모든 캐시 쓰기는 Lua 스크립트로 캐시에 있는 값의 `version`(`updatedAt`)과 비교해 더 오래된 값을 거부한다. 삭제 직전 값을 읽은 조회 요청이 삭제 뒤에 캐시를 덮어쓰는 경쟁을 막기 위해, 삭제는 키를 지우지 않고 `deleted` 상태로 덮어쓴다.
- 삭제 상태 반영은 즉시 2회 재시도(50ms, 100ms) 후 백그라운드에서 1·2·4·…·64초 간격으로 다시 시도한다. 끝내 실패하면 ERROR 로그와 `file.cache.update.failures` 지표를 남긴다. 이때도 다운로드는 DB 기준이라 막히고, 메타데이터 조회만 최대 TTL 5분 동안 삭제 전 정보를 보일 수 있다.
- 백그라운드 재시도는 메모리에만 있어 프로세스가 끝나면 사라진다. TTL이 노출 시간의 상한이므로 별도 영속 큐를 두지 않았다.

### 서버리스 후처리 흐름

아래는 요구사항의 목표 흐름이다. 현재 Lambda는 확장자 기반 가드·기본 메타데이터 추출·S3 object tag 기록까지만 하며, 실제 백신 엔진과 DB 상태 동기화는 FILE-03 범위다. 아래 목표를 현재 구현으로 해석하지 않는다.

1. 파일이 S3에 저장되면 ObjectCreated 이벤트가 발생한다.
2. S3 이벤트가 Lambda를 호출한다.
3. Lambda가 바이러스 검사와 메타데이터 추출을 수행한다.
4. Lambda가 검사 결과와 추출 메타데이터를 PostgreSQL 또는 Aurora에 반영한다.
5. 필요 시 CloudWatch에 로그와 지표를 남긴다.
6. 서버리스 작업 결과는 운영 이력과 추적 가능해야 한다.

## 책임 분리

| 구성요소 | 책임 |
| --- | --- |
| nginx / ALB | 외부 진입점, TLS 종료(AWS), 요청 전달, health check |
| backend | 인증·인가, 업로드 검증, 메타데이터 처리, 업로드 실패 복구, 다운로드 제어 |
| PostgreSQL / Aurora | 원본 데이터: 사용자, 파일 메타데이터, 업로드 요청 기록 |
| Redis / ElastiCache | 메타데이터 단건 조회 캐시. 원본이 아니다 |
| MinIO / S3 | 파일 바이너리 |
| Lambda | S3 이벤트 기반 후처리(현재 확장자 가드·태그) |

## 온프레미스 운영 기준

- 외부 API 진입점은 nginx 하나로 유지한다.
- 로컬 검증 편의를 위해 `5432`, `6379`, `9000`, `9001` 포트를 연다. 운영에 가까운 구성에서는 닫는다.
- 데이터는 Docker volume에 저장해 컨테이너 재생성 후에도 유지한다.
- 파일 삭제는 논리 삭제이며 객체의 물리 정리는 별도 운영 절차다. 업로드 실패로 남은 객체는 관리자 점검 API로 정리한다.
- JWT secret, DB 비밀번호, MinIO 계정은 `.env`로 주입하고 Git에 올리지 않는다.

## ECS 배포와 스케일링 책임

IAC-02에서 Terraform은 ECS 인프라와 task definition 템플릿을, CD는 service가 실행할 revision을, Application Auto Scaling은 생성 후 task 수를 관리하도록 분리한다. Service 생성 시에는 Terraform의 템플릿과 초기 task 수를 사용하며 갱신 시 `task_definition`·`desired_count`만 제외한다. 네트워크·로드밸런서·태그와 scaling 범위·정책은 Terraform 관리 대상이다.

템플릿 변경은 현재 서비스에 자동 반영되지 않는다. 현재 workflow는 서비스의 revision을 복제해 이미지만 교체하므로 설정 변경에는 별도 명시적 배포가 필요하다. 실행·재생성·검증 절차는 [배포 기준](design/DEPLOYMENT.md#ecs-관리-책임과-배포-순서-iac-02)을 따른다. 실제 AWS 검증 상태는 [진행 요약](PROGRESS.md)에 구분한다.

## 서버리스 책임

- S3는 업로드 완료 이벤트의 발생 지점이다.
- Lambda는 애플리케이션 본체에서 분리할 수 있는 후처리를 맡는다.
- CloudWatch는 Lambda 실행 결과와 오류를 추적한다.
- 서버리스 작업은 파일 저장과 메타데이터 저장의 핵심 경로를 대체하지 않는다.

## 상세 설계 탐색

| 범위 | 기준 문서 |
| --- | --- |
| 데이터 모델·상태·관계 | [DATABASE](design/DATABASE.md) |
| 요청·응답·오류 계약 | [API](design/API.md) |
| 온프레미스 → AWS 전환 | [MIGRATION](design/MIGRATION.md) |
| 로컬 호스트·온프레미스·AWS 배포 | [DEPLOYMENT](design/DEPLOYMENT.md) |
| AWS 모듈·네트워크·권한·운영 한계 | [AWS](design/AWS.md) |
| 검증 시나리오 | [VALIDATION](design/VALIDATION.md) |

현재 상태는 [PROGRESS](PROGRESS.md), 수행 결과는 작업일지, 승인 근거는 [DECISIONS](DECISIONS.md)를 따른다.
