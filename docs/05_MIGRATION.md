# 05. Migration

## Migration Strategy

온프레미스에서 구현한 기능과 데이터 계약을 유지한 채, 저장소와 실행 환경만 단계적으로 AWS로 전환한다.

## 전제

- 애플리케이션의 기능 계약은 온프레미스와 AWS에서 동일하게 유지한다.
- 파일 상태와 삭제 정책은 `docs/00_REQUIREMENTS.md` 기준을 따른다.
- DB 스키마와 API 응답 형식은 가능한 한 변경하지 않는다.

### Storage

- MinIO -> Amazon S3
- 업로드 경로와 URL 생성 로직을 추상화
- S3 이벤트 기반 후처리는 Lambda로 분리

### Serverless

- 파일 업로드 후 필요한 비동기 작업인 바이러스 검사와 메타데이터 추출을 Lambda로 분리
- S3 이벤트를 트리거로 사용하는 후처리 구조로 전환
- 서버리스 실행 결과를 CloudWatch에서 추적

### Database

- PostgreSQL -> Amazon Aurora PostgreSQL
- 스키마 호환성 유지
- 마이그레이션 전후 데이터 검증 수행

### Cache

- Redis -> Amazon ElastiCache
- 캐시 키 구조를 유지해 교체 비용을 낮춘다

### Runtime

- Docker Compose -> Amazon ECS Fargate
- Spring Boot 이미지를 ECR로 배포
- ECS Service desired count와 Service Auto Scaling으로 확장

## 전환 순서

1. 애플리케이션 코드에서 저장소 추상화
2. AWS 리소스 준비
3. 테스트 환경에서 S3/Aurora/ElastiCache/Lambda 연결
4. 배포 파이프라인 변경
5. 트래픽 전환
6. 검증 및 최종 안정화

## Day 2 구현 기준

- Spring Boot 백엔드는 온프레미스 구현을 우선하되 패키지 경계를 `infra/storage`로 분리해 MinIO에서 S3로 교체 가능한 구조를 준비한다.
- API 응답 형식과 에러 코드는 온프레미스와 AWS 배포에서 동일하게 유지한다.
- 보안 설정은 stateless JWT 인증을 기준으로 두어 Docker Compose와 ECS 양쪽에서 세션 저장소 의존성을 만들지 않는다.
- `GET /api/health`와 Actuator health endpoint를 유지해 Docker Compose와 ALB target group health check에서 같은 상태 확인 기준을 사용할 수 있게 한다.

## Day 3 구현 기준

- 사용자 저장소는 Day 5 PostgreSQL 연동 전까지 인메모리 구현을 사용한다.
- `UserRepository` 인터페이스를 기준으로 서비스 계층을 작성해 PostgreSQL/Aurora 전환 시 컨트롤러와 인증 서비스 계약을 유지한다.
- JWT access token은 stateless 구조로 유지해 Docker Compose와 ECS 배포 간 세션 저장소 차이를 만들지 않는다.
- 로컬 `app.jwt.secret`은 개발용 값이며, 운영/AWS 배포에서는 ECS task secret 또는 AWS Secrets Manager로 분리한다.
- Refresh token과 logout token blacklist는 Redis/ElastiCache 연동 이후 저장소 계약을 확정한다.

## Day 4 구현 기준

- 파일 메타데이터는 `FileMetadataRepository` 인터페이스 뒤에 두고, Day 4에서는 인메모리 구현을 사용한다.
- 파일 바이너리 저장은 `FileStorage` 인터페이스 뒤에 두고, Day 4에서는 로컬 파일 시스템 구현을 사용한다.
- 업로드 API의 외부 계약은 저장소 구현과 분리해 유지하므로 Day 5 MinIO, Day 9 S3 전환 시 컨트롤러 계약을 바꾸지 않는다.
- Day 4 로컬 구현은 바이러스 검사와 메타데이터 추출 후처리를 수행하지 않으며, 업로드 직후 `available/CLEAN` 상태로 등록한다.
- AWS 전환 시 `LocalFileStorage`는 S3 기반 구현으로 교체하고 `storagePath`는 S3 object key 또는 URI 계약으로 유지한다.
- 논리 삭제 정책은 온프레미스와 AWS에서 동일하게 유지하고, 실제 object 정리는 별도 배치 또는 lifecycle 정책으로 분리한다.

## Day 5 구현 기준

- `postgres` 프로필에서 사용자와 파일 메타데이터 저장소가 JPA/PostgreSQL 어댑터로 전환된다.
- JPA 엔티티는 도메인 모델과 분리해 두어 API 계층과 서비스 계층의 계약을 유지한다.
- `redis` 프로필에서 파일 단건 메타데이터 캐시가 활성화되며, 캐시 키는 `files:metadata:{fileId}` 형식을 사용한다.
- 업로드와 단건 조회 시 메타데이터 캐시를 갱신하고, 논리 삭제 시 캐시를 제거한다.
- `minio` 프로필에서 `FileStorage` 구현이 로컬 파일 시스템에서 MinIO object storage로 전환된다.
- MinIO의 `storagePath`는 object key로 저장하므로 Day 9 S3 전환 시 S3 object key 계약으로 그대로 이어갈 수 있다.
- AWS 전환 시 PostgreSQL은 Aurora PostgreSQL, Redis는 ElastiCache, MinIO는 S3 구현체로 교체한다.

## Day 6 구현 기준

- 온프레미스 실행 단위는 Docker Compose로 고정하고, PostgreSQL, Redis, MinIO, Spring Boot, Nginx를 하나의 로컬 스택으로 실행한다.
- 외부 트래픽은 Nginx를 통해 `/api/**`와 `/actuator/health`만 Spring Boot로 전달한다.
- Spring Boot 컨테이너는 `postgres,redis,minio` 프로필을 활성화해 온프레미스의 실제 저장소 조합을 사용한다.
- DB, Redis, MinIO, JWT, 업로드 제한 설정은 환경변수로 분리해 ECS task definition 또는 AWS Secrets Manager로 옮길 수 있게 한다.
- Compose의 PostgreSQL, Redis, MinIO 볼륨은 AWS 전환 전 데이터 검증 기준으로 사용한다.
- AWS 전환 시 Nginx reverse proxy 역할은 ALB로 대체하고, 애플리케이션 컨테이너 이미지는 ECR에 푸시한다.

## Day 7 검증 기준

- Docker Compose 온프레미스 환경에서 검증한 API 계약을 AWS 전환 후에도 유지한다.
- PostgreSQL `users`, `files` 데이터는 Aurora PostgreSQL 이전 검증 기준으로 사용한다.
- MinIO object key와 `files.storage_path` 계약은 S3 object key 계약으로 이어간다.
- Redis 캐시 키 `files:metadata:{fileId}`는 ElastiCache 전환 후에도 동일하게 유지한다.
- Nginx health/API 진입점 검증은 ALB health check와 ECS service 라우팅 검증 기준으로 전환한다.
- 장애 시나리오에서 확인한 DB, Redis, object storage 의존성은 AWS 보안 그룹, subnet, IAM, ECS service health check 설계에 반영한다.

## Day 8 AWS 인프라 설계 기준

- Terraform은 `terraform/environments/dev`와 `terraform/modules/*` 구조로 시작한다.
- VPC는 public, private, database subnet 3계층으로 분리한다.
- ALB와 NAT Gateway는 public subnet에 둔다.
- ECS Fargate task는 private subnet에 둔다.
- Aurora PostgreSQL과 ElastiCache Redis는 database subnet에 둔다.
- S3는 MinIO의 `files` bucket 역할을 대체하며, object key는 기존 `storagePath` 계약과 호환되게 유지한다.
- ECR은 Docker Compose에서 빌드하던 Spring Boot 이미지를 ECS 배포용 이미지로 보관한다.
- dev ECR repository는 실습 후 `terraform destroy`가 image 잔존으로 막히지 않도록 `force_delete = true`를 사용한다. 운영 전환 시에는 repository 삭제 정책과 image 보존 요구사항을 별도로 검토한다.
- 애플리케이션의 S3 접근 권한은 IAM policy로 분리하고, ECS task role에 연결한다.
- Route53과 ACM은 Day 12 HTTPS 전환에서 적용하되, Day 8 설계 범위에는 ALB HTTPS 진입점의 선행 리소스로 포함한다.
- Terraform 변수는 `aws_region`, `project_name`, `environment`, VPC/subnet CIDR, S3 bucket 이름을 환경별로 분리한다.
- Terraform state는 로컬 개발에서 시작할 수 있지만, 협업 또는 운영 전환 시 S3 backend와 DynamoDB lock table로 이전한다.

## Day 9 AWS 전환 기준

- ECS cluster와 Fargate service를 생성하고 application task는 private subnet에 배치한다.
- ALB는 public subnet에 두고 target group health check는 `/api/health`를 사용한다.
- S3 접근 권한은 IAM user/access key가 아니라 ECS task role에 policy를 attach하는 방식으로 연결한다.
- Aurora PostgreSQL은 database subnet에 두고 application SG에서 오는 5432 트래픽만 허용한다.
- ElastiCache Redis는 database subnet에 두고 application SG에서 오는 6379 트래픽만 허용한다.
- Redis는 전송 중 암호화를 켜므로 AWS 배포에서는 `REDIS_SSL_ENABLED=true`를 주입한다.
- Spring Boot는 AWS 배포에서 `postgres,redis,s3` profile을 사용한다.
- S3 구현체는 기존 `FileStorage` 인터페이스를 유지하고, object key를 `files.storage_path`에 저장한다.
- Aurora password, JWT secret, Redis endpoint, S3 bucket name은 ECS task definition 환경변수 또는 AWS Secrets Manager로 주입한다.

## Day 10 ECS 배포 및 CI/CD 기준

- 실제 AWS 리소스 생성은 Terraform apply 시점까지 미룬다.
- CI/CD는 Terraform으로 이미 생성된 ECR repository와 ECS service를 대상으로 동작한다.
- GitHub Actions는 backend test, Docker image build, ECR push, ECS service rolling deployment를 수행한다.
- 배포 image tag는 `github.sha`를 사용해 어떤 commit이 실행 중인지 추적할 수 있게 한다.
- ECS task definition은 현재 service의 task definition을 조회한 뒤 backend container image만 교체해 새 revision으로 배포한다.
- ALB target group health check를 통과한 task만 트래픽을 받는다.
- AWS 인증은 장기 access key 대신 GitHub OIDC로 assume하는 deploy role을 기준으로 한다.

## Day 11 Lambda, 모니터링, 스케일링 기준

- S3 object 생성 이벤트는 `file-processor` Lambda를 호출한다.
- Lambda는 업로드 object의 크기와 content type을 조회하고, 처리 결과를 S3 object tag로 기록한다.
- 기본 후처리 tag는 `scan-status`, `processing-status`, `metadata-size`, `metadata-content-type`, `processor`이다.
- 현재 Lambda의 바이러스 검사 구현은 차단 확장자 기반의 기본 가드이며, 실제 운영 전환 시 ClamAV layer 또는 별도 검사 서비스 연동으로 교체한다.
- Lambda 후처리 결과를 애플리케이션 DB의 `files.status`, `files.scan_status`와 동기화하는 단계는 후속 작업으로 분리한다.
- Lambda 실행 role은 업로드 bucket의 object read/tagging 권한과 자체 CloudWatch Logs 쓰기 권한만 가진다.
- ECS service는 Service Auto Scaling target tracking으로 CPU와 memory 사용률 기준 확장을 수행한다.
- ECS Container Insights, CloudWatch Logs, Lambda error/duration alarm, ECS CPU/memory alarm, ALB target 5xx alarm을 관측성 기준으로 둔다.
- 롤링 배포의 정상 여부는 ECS service event, ALB target health, `/api/health`, CloudWatch application log를 함께 확인한다.

## Day 12 HTTPS 및 보안 강화 기준

- `domain_name`과 `route53_hosted_zone_name`이 설정된 환경에서는 Route53, ACM, ALB HTTPS listener를 Terraform으로 연결한다.
- ACM 인증서는 DNS validation을 사용하고, 검증 레코드는 같은 public hosted zone에 생성한다.
- HTTPS가 켜진 경우 ALB 443 listener가 ECS target group으로 요청을 전달하고, 80 listener는 443으로 redirect한다.
- 도메인 변수가 비어 있으면 개발 실습을 위해 기존 HTTP ALB 진입점을 유지한다.
- 애플리케이션 S3 object key는 `S3_OBJECT_PREFIX` 기본값인 `files/` 아래에 생성한다.
- ECS task role의 S3 object 권한은 bucket 전체가 아니라 `files/` prefix로 제한한다.
- file processor Lambda의 S3 read/tagging 권한도 S3 notification prefix와 같은 prefix로 제한한다.
- 공개 subnet에는 ALB, NAT Gateway, Internet Gateway를 둔다.
- private subnet에는 ECS Fargate task를 둔다.
- database subnet에는 Aurora PostgreSQL과 ElastiCache Redis를 둔다.
- 운영 전환 시 S3/ECR/CloudWatch 접근은 VPC endpoint와 egress 제한을 함께 검토한다.

## Day 13 최종 검증 및 성능 점검 기준

- 최종 검증은 온프레미스 Docker Compose와 AWS ECS 환경의 API 계약을 비교하는 방식으로 수행한다.
- 사용자 관점의 핵심 시나리오는 health, signup, login, current user, upload, list, metadata lookup, download, delete이다.
- 온프레미스 MinIO object key와 AWS S3 object key는 모두 `files.storage_path` 계약으로 비교한다.
- AWS S3 object key는 `files/` prefix 아래 생성되어야 하며, prefix 제한 IAM 정책과 Lambda notification prefix가 같은 기준을 사용해야 한다.
- PostgreSQL과 Aurora PostgreSQL은 같은 `users`, `files` 테이블 계약을 유지한다.
- Redis와 ElastiCache는 `files:metadata:{fileId}` 캐시 키 형식을 유지한다.
- 장애 복구 검증은 온프레미스의 컨테이너 중지/재시작과 AWS의 실제 실패 주입으로 나누어 수행한다. AWS에서는 ECS task `stop-task` 후 service scheduler 복구, Lambda malformed event 실패 후 정상 업로드 재처리, CloudWatch Logs/Alarms 확인까지 포함한다.
- 성능 점검은 개발 환경 비용을 고려해 smoke 수준의 반복 health/API 호출로 시작하고, 본격 부하 테스트는 별도 예산과 알람 action을 준비한 뒤 수행한다.
- 최종 비교는 단순 기능 동일성뿐 아니라 파일 관리, 비용 조정, 보안 경계, 장애 복구, 로그/관측성, 확장성 측면에서 AWS 전환 효과를 설명할 수 있어야 한다.
- 파일 관리는 S3 object key, S3 object tag, Lambda 실행 로그를 함께 확인해 저장과 후처리 책임이 backend에서 분리되었는지 검증한다.
- 비용 관리는 ECS desired count와 Auto Scaling 범위, Lambda event 기반 실행, CloudWatch log retention, destroy 후 잔여 리소스 여부를 확인한다.
- 보안 검증은 public/private/database subnet 분리, security group 접근 경로, ECS/Lambda IAM role의 prefix 기반 S3 권한, ACM HTTPS 적용 여부를 기준으로 한다.
- 관측성 검증은 backend log, Lambda log, ALB 5xx, ECS CPU/memory, Lambda error/duration alarm을 CloudWatch에서 확인하는 방식으로 수행한다.

## Day 14 포트폴리오 및 마감 기준

- 최종 산출물은 기능 구현 목록이 아니라 온프레미스에서 AWS로 책임이 어떻게 이동했는지를 중심으로 정리한다.
- API 계약, DB schema, object key, cache key처럼 마이그레이션 중 유지한 계약을 포트폴리오의 핵심 근거로 사용한다.
- MinIO에서 S3로의 전환은 `files.storage_path`와 object key 계약 유지, S3 event 기반 Lambda 후처리 분리, prefix 기반 IAM 제한을 함께 설명한다.
- PostgreSQL에서 Aurora로의 전환은 테이블 계약 유지와 database subnet 격리를 함께 설명한다.
- Redis에서 ElastiCache로의 전환은 `files:metadata:{fileId}` 캐시 키 유지와 managed cache 운영 전환을 함께 설명한다.
- Docker Compose/Nginx에서 ECS/ALB로의 전환은 health check, target group, rolling deployment, scheduler self-healing 기준으로 설명한다.
- CloudWatch 지표는 CPU/memory 단일 수치가 아니라 ALB 5xx, target health, Lambda error, ECS task 재시작 여부와 함께 장애 판단 기준으로 정리한다.
- Day 14 마감 후 남은 작업은 운영 보강 항목으로 분리하고, 현재 프로젝트는 2주 마이그레이션 포트폴리오 산출물 완성을 완료 기준으로 둔다.
