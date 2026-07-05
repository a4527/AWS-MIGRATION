# 02. Architecture

## 온프레미스 아키텍처

```text
Client
  -> Nginx Reverse Proxy
  -> Spring Boot API
  -> PostgreSQL
  -> Redis
  -> MinIO
```

Day 6 기준 각 구성요소는 Docker Compose 서비스로 실행한다. Client의 외부 진입점은 Nginx이고, Spring Boot 컨테이너는 내부 네트워크에서 PostgreSQL, Redis, MinIO에 접근한다.

## 데이터 흐름

### 인증 흐름

1. Client가 로그인 요청을 보낸다.
2. Nginx가 요청을 Spring Boot API로 전달한다.
3. Spring Boot가 PostgreSQL에서 사용자 정보를 조회한다.
4. 인증 성공 시 JWT를 발급한다.
5. Client는 이후 요청에 JWT를 포함한다.

### 파일 업로드 흐름

1. Client가 `multipart/form-data` 요청으로 파일을 전송한다.
2. Nginx가 업로드 요청을 Spring Boot API로 전달한다.
3. Spring Boot가 파일 크기, 확장자, 권한을 검증한다.
4. 파일 바이너리는 MinIO에 저장한다.
5. 메타데이터는 PostgreSQL에 저장한다.
6. 빈번한 조회를 위해 필요한 경우 Redis에 캐시를 반영한다.

### 파일 다운로드 흐름

1. Client가 파일 다운로드를 요청한다.
2. Spring Boot가 권한과 파일 상태를 확인한다.
3. Spring Boot가 MinIO에서 바이너리를 조회한다.
4. 파일 스트림을 Client로 반환한다.

### 파일 삭제 흐름

1. Client가 삭제 요청을 보낸다.
2. Spring Boot가 권한을 확인한다.
3. 파일 상태를 `deleted`로 변경한다.
4. 메타데이터는 즉시 논리 삭제로 반영한다.
5. 실제 바이너리 정리는 후속 운영 절차로 분리할 수 있다.

### 서버리스 후처리 흐름

1. 파일이 S3에 저장되면 ObjectCreated 이벤트가 발생한다.
2. S3 이벤트가 Lambda를 호출한다.
3. Lambda가 바이러스 검사와 메타데이터 추출을 수행한다.
4. Lambda가 검사 결과와 추출 메타데이터를 PostgreSQL 또는 Aurora에 반영한다.
5. 필요 시 CloudWatch에 로그와 지표를 남긴다.
6. 서버리스 작업 결과는 운영 이력과 추적 가능해야 한다.

### 구성 역할

- Nginx: 외부 진입점, TLS 종료 지점, reverse proxy, 요청 라우팅
- Spring Boot: 인증, 인가, 파일 메타데이터 처리, 권한 검증, 업로드/다운로드 제어
- PostgreSQL: 사용자 계정, 파일 메타데이터, 권한 정보, 운영 로그 저장
- Redis: 자주 조회되는 메타데이터 캐시, 단기 조회 성능 개선
- MinIO: 실제 파일 바이너리 저장소

### Docker Compose 서비스

- `nginx`: 호스트 `8080` 포트에서 요청을 수신하고 백엔드로 프록시한다.
- `backend`: Spring Boot API를 실행하며 `postgres,redis,minio` 프로필을 사용한다.
- `postgres`: 사용자와 파일 메타데이터를 저장한다.
- `redis`: 파일 단건 메타데이터 캐시를 저장한다.
- `minio`: 파일 바이너리를 object key 기준으로 저장한다.

## 책임 분리

- 요청 라우팅과 연결 관리는 Nginx가 담당한다.
- 비즈니스 규칙과 권한 판단은 Spring Boot가 담당한다.
- 영속 데이터는 PostgreSQL이 담당한다.
- 캐시성 데이터는 Redis가 담당한다.
- 파일 본문은 MinIO가 담당한다.

## 온프레미스 운영 기준

- 외부 API 진입점은 Nginx 하나로 유지한다.
- Spring Boot, PostgreSQL, Redis, MinIO는 Docker Compose 내부 네트워크에서 서비스 이름으로 통신한다.
- 운영에 가까운 구성에서는 PostgreSQL, Redis, MinIO의 호스트 포트 공개를 제거하고 내부 통신만 허용한다.
- 로컬 검증 단계에서는 DB/캐시/스토리지 확인 편의를 위해 `5432`, `6379`, `9000`, `9001` 포트를 노출한다.
- PostgreSQL, Redis, MinIO 데이터는 Docker volume에 저장해 컨테이너 재생성 이후에도 유지한다.
- 파일 삭제는 논리 삭제이며 MinIO object의 물리 정리는 별도 운영 절차로 분리한다.
- JWT secret, DB 비밀번호, MinIO 계정은 운영 배포에서 `.env` 또는 secret 저장소로 분리한다.

## Day 7 검증 결과 기준

- Nginx를 통한 health, 인증, 사용자, 파일 API 호출 흐름을 검증한다.
- PostgreSQL `users`, `files` 테이블에 사용자와 파일 메타데이터가 저장되는지 확인한다.
- Redis `files:metadata:{fileId}` 캐시 키 생성과 TTL을 확인한다.
- MinIO `files` 버킷에 업로드 object가 저장되는지 확인한다.
- 인증 누락, 잘못된 확장자, 일반 사용자 권한 부족 등 실패 응답이 API 계약과 맞는지 확인한다.

## AWS Architecture

```text
Client
  -> Route53
  -> ALB (public subnet)
  -> Amazon ECS Fargate (private subnet)
  -> Aurora PostgreSQL (database subnet)
  -> ElastiCache Redis (database subnet)
  -> Amazon S3
  -> Lambda
  -> CloudWatch
```

### 구성 역할

- Route53: 도메인 라우팅
- ALB: HTTPS 진입점 및 트래픽 분산
- ECS Fargate: Spring Boot 애플리케이션 실행 환경
- ECR: Spring Boot 컨테이너 이미지 저장소
- IAM: ECS task, 애플리케이션, Lambda가 AWS 리소스에 접근하기 위한 권한 경계
- Aurora: 관리형 관계형 DB
- ElastiCache: 관리형 캐시
- S3: 파일 저장소 및 이벤트 발생 지점
- Lambda: S3 이벤트 기반 바이러스 검사, 메타데이터 추출, 비동기 보조 작업
- CloudWatch: 로그, 메트릭, 알람

### Day 8 AWS 네트워크 설계

- VPC CIDR은 개발 환경 기준 `10.20.0.0/16`으로 둔다.
- Public subnet은 2개 AZ에 배치하고 ALB와 NAT Gateway를 둔다.
- Private subnet은 2개 AZ에 배치하고 ECS Fargate task를 둔다.
- Database subnet은 2개 AZ에 배치하고 Aurora PostgreSQL과 ElastiCache Redis를 둔다.
- Database subnet은 기본 인터넷 라우트를 두지 않는다.
- 개발 비용 절감을 위해 Day 8 Terraform 초안은 NAT Gateway 1개로 시작한다. 운영 환경은 AZ별 NAT Gateway 구성을 검토한다.

### Day 8 AWS 리소스 범위

- Terraform 환경은 `terraform/environments/dev`에서 시작한다.
- 공통 모듈은 `terraform/modules` 아래 `vpc`, `ecr`, `s3`, `iam`으로 분리한다.
- ECS, ALB, Route53, ACM, Aurora, ElastiCache, Lambda, CloudWatch는 Day 9 이후 모듈로 확장한다.
- S3 bucket은 public access block, SSE-S3 암호화, versioning, multipart upload 정리 정책을 기본값으로 둔다.
- ECR은 backend image repository와 최근 이미지 20개 보관 lifecycle policy를 둔다.
- 애플리케이션 IAM은 Day 8에서는 S3 접근 policy만 정의하고, Day 9 ECS task role에 연결한다.

### Day 9 AWS 리소스 범위

- Terraform 모듈은 `security-groups`, `ecs`, `aurora`, `elasticache`를 추가한다.
- Security Group은 ALB, application runtime, Aurora PostgreSQL, Redis 용도로 분리한다.
- Application SG는 ALB SG에서 오는 8080 트래픽을 허용하고, Aurora SG와 Redis SG는 application SG에서 오는 포트만 허용한다.
- ECS는 private subnet에 Fargate service를 두고, 애플리케이션 task role을 생성한다.
- 애플리케이션 task role에는 S3 업로드 버킷 접근 policy를 attach한다.
- Aurora PostgreSQL은 database subnet group에 배치하고 storage encryption과 Serverless v2 scaling을 사용한다.
- ElastiCache Redis는 database subnet group에 배치하고 저장/전송 암호화를 켠다.
- Spring Boot는 `s3` profile에서 S3 bucket을 직접 사용하며, `storagePath`는 MinIO와 동일하게 object key로 유지한다.

## 서버리스 책임

- S3는 업로드 완료 이벤트의 발생 지점이 된다.
- Lambda는 애플리케이션 본체에서 분리할 수 있는 바이러스 검사와 메타데이터 추출을 담당한다.
- Lambda는 검사 결과와 추출 메타데이터를 저장 계층에 반영한다.
- CloudWatch는 Lambda 실행 결과와 오류를 추적한다.
- 서버리스 작업은 파일 저장과 메타데이터 저장의 핵심 경로를 대체하지 않는다.

## 온프레미스와 AWS 비교 기준

- 애플리케이션 기능은 동일하게 유지한다.
- 저장소는 MinIO에서 S3로 교체한다.
- DB는 PostgreSQL에서 Aurora PostgreSQL로 교체한다.
- 캐시는 Redis에서 ElastiCache로 교체한다.
- 실행 환경은 Docker Compose에서 ECS Fargate로 교체한다.

## 마이그레이션 원칙

- 기능은 동일하게 유지하고 인프라만 교체한다.
- 파일 저장 방식은 MinIO에서 S3로 교체한다.
- DB는 PostgreSQL 호환성을 유지한 채 Aurora로 이동한다.
- 캐시는 Redis에서 ElastiCache로 전환한다.
- 배포 방식은 Docker Compose에서 ECS service 배포로 변경한다.

## Day 1 기준

- 온프레미스 요청 흐름이 문서화된다.
- 각 구성요소의 책임이 분리된다.
- AWS 섹션은 비교 기준만 제공하고 온프레미스 설명과 섞지 않는다.
