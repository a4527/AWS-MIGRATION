# 08. Portfolio

## 프로젝트 소개

기업 내부 파일 공유 시스템을 온프레미스 Docker Compose 환경에서 먼저 구현하고, 같은 API 계약과 데이터 모델을 유지한 채 AWS 클라우드 네이티브 구조로 전환한 2주 프로젝트다.

핵심 목표는 단순히 애플리케이션을 AWS에 올리는 것이 아니라, 온프레미스 운영에서 직접 관리하던 영역을 AWS 관리형 서비스와 서버리스 구성으로 분리해 운영 부담을 줄이는 것이다.

## 프로젝트 스토리

1주차에는 파일 공유 서비스의 기본 기능을 온프레미스 기준으로 구현했다.

- Spring Boot 기반 API 서버를 구성했다.
- JWT 인증과 사용자 API를 구현했다.
- 파일 업로드, 다운로드, 삭제 API를 구현했다.
- PostgreSQL, Redis, MinIO를 Docker Compose로 묶어 로컬 운영 환경을 만들었다.
- Nginx reverse proxy를 통해 외부 진입점을 단일화했다.
- API 문서, ERD, 배포 문서, 수동 검증 문서를 작성했다.

2주차에는 온프레미스 구성요소를 AWS 서비스로 바꾸는 마이그레이션을 진행했다.

- PostgreSQL container는 Aurora PostgreSQL로 전환했다.
- Redis container는 ElastiCache Redis로 전환했다.
- MinIO object storage는 S3로 전환했다.
- Nginx reverse proxy는 ALB와 target group health check로 대체했다.
- backend container는 ECS Fargate service로 실행했다.
- 파일 업로드 후처리는 S3 event 기반 Lambda로 분리했다.
- Terraform으로 VPC, ECS, ALB, Aurora, ElastiCache, S3, IAM, CloudWatch, Route53/ACM 구성을 코드화했다.
- GitHub Actions OIDC 기반 CI/CD 배포 흐름을 정리했다.
- CloudWatch Logs/Alarms와 ECS Auto Scaling 기준을 추가했다.

## 아키텍처 요약

### 온프레미스

```text
Client
  -> Nginx
  -> Spring Boot API
  -> PostgreSQL
  -> Redis
  -> MinIO
```

온프레미스 환경은 Docker Compose 하나로 실행할 수 있도록 구성했다. 빠른 개발과 통합 검증에는 유리하지만, 장애 복구, 확장, 인증서 관리, 로그 중앙화는 운영자가 직접 책임져야 한다.

### AWS

```text
Client
  -> Route53 / ACM
  -> ALB
  -> ECS Fargate Spring Boot API
  -> Aurora PostgreSQL
  -> ElastiCache Redis
  -> S3
  -> Lambda file processor
  -> CloudWatch Logs / Alarms
```

AWS 환경에서는 public, private, database subnet을 분리했다. ALB만 public subnet에 노출하고, ECS task는 private subnet, Aurora와 ElastiCache는 database subnet에 배치했다. S3 접근은 IAM role과 object prefix 기준으로 제한했다.

## 주요 기능

- 회원가입, 로그인, JWT 기반 인증
- 현재 사용자 조회 및 프로필 수정
- 파일 업로드, 목록 조회, 단건 조회, 다운로드, 논리 삭제
- 파일 확장자와 크기 검증
- PostgreSQL 기반 사용자/파일 메타데이터 저장
- Redis 기반 파일 메타데이터 캐시
- MinIO/S3 기반 파일 바이너리 저장
- S3 event 기반 Lambda 후처리
- CloudWatch 기반 로그와 알람 확인

## 기술 선택 이유

| 영역 | 선택 | 이유 |
| --- | --- | --- |
| Backend | Spring Boot | 인증, REST API, 테스트, 운영 설정을 안정적으로 구성하기 좋다. |
| Auth | Spring Security, JWT | Docker Compose와 ECS 양쪽에서 세션 저장소 없이 같은 인증 계약을 유지할 수 있다. |
| Database | PostgreSQL, Aurora PostgreSQL | 온프레미스와 AWS 간 SQL/스키마 호환성을 유지하기 쉽다. |
| Cache | Redis, ElastiCache Redis | 파일 메타데이터 단건 조회 캐시를 같은 key 계약으로 이전할 수 있다. |
| Object Storage | MinIO, S3 | S3 호환 object key 계약을 먼저 잡아 AWS 전환 비용을 낮춘다. |
| Runtime | Docker Compose, ECS Fargate | 로컬 통합 실행과 AWS managed container runtime을 단계적으로 비교할 수 있다. |
| IaC | Terraform | 네트워크, 보안, 런타임, 관측성 리소스를 재현 가능한 코드로 관리한다. |
| CI/CD | GitHub Actions OIDC | 장기 access key 없이 ECR push와 ECS rolling deployment를 수행한다. |
| Serverless | Lambda | 파일 후처리를 backend API 응답 경로에서 분리하고 event 기반으로 확장한다. |
| Observability | CloudWatch | ECS, ALB, Lambda 로그와 지표를 한 곳에서 추적한다. |

## 마이그레이션 포인트

| 온프레미스 | AWS | 유지한 계약 |
| --- | --- | --- |
| Nginx | ALB | `/api/**`, `/actuator/health` 진입 경로 |
| Spring Boot container | ECS Fargate task | API 요청/응답 형식 |
| PostgreSQL | Aurora PostgreSQL | `users`, `files` 테이블 구조 |
| Redis | ElastiCache Redis | `files:metadata:{fileId}` 캐시 키 |
| MinIO | S3 | `files.storage_path`와 object key |
| 로컬 로그 | CloudWatch Logs | backend 로그 추적 기준 |
| 수동 복구 | ECS service scheduler | health check 기반 복구 기준 |

## 운영 검증 포인트

- API 계약: health, signup, login, current user, file workflow가 온프레미스와 AWS에서 동일하게 동작해야 한다.
- 파일 저장: DB의 `files.storage_path`와 object storage key가 일치해야 한다.
- 후처리: S3 object 생성 이벤트가 Lambda를 호출하고, 처리 결과가 object tag와 Lambda log에 남아야 한다.
- 장애 복구: ECS task 중지 후 service scheduler가 desired count를 복구해야 한다.
- 알람: Lambda error/duration, ECS CPU/memory, ALB target 5xx alarm을 CloudWatch에서 확인할 수 있어야 한다.
- 보안: ALB는 public, ECS는 private, Aurora/ElastiCache는 database subnet에 있어야 한다.

## 결과 이미지와 다이어그램 정리

포트폴리오에 넣을 이미지는 다음 순서로 배치한다.

1. 온프레미스 아키텍처 다이어그램: `docs/assets/onprem-architecture.svg`
2. AWS 목표 아키텍처 다이어그램: `docs/assets/aws-architecture.svg`
3. 업로드와 서버리스 후처리 흐름: `docs/assets/file-upload-flow.svg`
4. Docker Compose 실행 화면 또는 API smoke test 결과
5. Terraform 리소스 요약 화면
6. ECS service와 ALB target health 화면
7. S3 object와 Lambda tag/log 확인 화면
8. CloudWatch alarm과 ECS 지표 화면
9. 운영 검증 요약 다이어그램: `docs/assets/operations-validation.svg`

CloudWatch 화면은 단독으로 보기보다 "어떤 기준으로 장애를 판단하는지"를 함께 설명한다. 예를 들어 ECS CPU는 순간 spike보다 지속적인 80% 이상 사용률과 ALB 응답 시간/5xx 증가를 함께 보고, 메모리는 85% 이상 지속 또는 task 재시작 여부를 함께 본다.

생성된 다이어그램과 실제 캡처가 필요한 항목은 `docs/assets/README.md`에서 관리한다.

## 산출물

- 요구사항: `docs/00_REQUIREMENTS.md`
- 로드맵: `docs/01_ROADMAP.md`
- 아키텍처: `docs/02_ARCHITECTURE.md`
- 데이터 모델: `docs/03_DATABASE.md`
- API 문서: `docs/04_API_SPEC.md`
- 마이그레이션 전략: `docs/05_MIGRATION.md`
- 배포 문서: `docs/06_DEPLOYMENT.md`
- 트러블슈팅: `docs/07_TROUBLESHOOTING.md`
- 작업 로그: `docs/09_WORK_LOG.md`
- 온프레미스 수동 검증: `docs/11_ONPREM_MANUAL_TEST.md`
- Terraform AWS 인프라 요약: `docs/12_TERRAFORM_AWS_INFRA_SUMMARY.md`
- AWS 배포 런북: `docs/13_AWS_DEPLOYMENT_RUNBOOK.md`
- 최종 검증: `docs/14_FINAL_VALIDATION.md`
- 포트폴리오 이미지: `docs/assets/README.md`

## 최종 정리

이 프로젝트는 기능 구현, 온프레미스 통합 실행, AWS 마이그레이션 설계, IaC, CI/CD, 서버리스 후처리, 관측성, 장애 복구 검증까지 하나의 흐름으로 연결한다.

포트폴리오에서는 "AWS 서비스를 사용했다"보다 다음 세 가지를 중심으로 설명한다.

- 같은 API/데이터 계약을 유지하면서 인프라를 교체했다.
- 파일 저장과 후처리 책임을 S3/Lambda로 분리했다.
- 장애 복구와 관측성을 CloudWatch, ALB health check, ECS scheduler 기준으로 검증했다.
