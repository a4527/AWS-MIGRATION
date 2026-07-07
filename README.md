# Enterprise File Sharing Platform Modernization

Docker Compose 기반 온프레미스 파일 공유 플랫폼을 먼저 구축하고, 이후 AWS(ECS Fargate, Lambda, Aurora, S3 등)로 마이그레이션하는 2주 프로젝트입니다.

## 목표

- Week 1: 온프레미스 환경을 Docker Compose로 구현
- Week 2: AWS 클라우드 네이티브 구조로 전환
- 최종 산출물: 온프레미스/클라우드 아키텍처, API 문서, ERD, Terraform, CI/CD, 포트폴리오 문서

## 핵심 구현

- Spring Boot, Spring Security, JWT 기반 파일 공유 API
- PostgreSQL 사용자/파일 메타데이터 저장
- Redis 파일 메타데이터 캐시
- MinIO와 S3를 교체 가능한 `FileStorage` 계층
- Docker Compose, Nginx 기반 온프레미스 실행 환경
- Terraform 기반 AWS dev 인프라
- ECS Fargate, ALB, Aurora PostgreSQL, ElastiCache Redis, S3 구성
- S3 event 기반 Lambda 파일 후처리
- GitHub Actions OIDC 기반 ECR/ECS 배포 흐름
- CloudWatch Logs/Alarms, ECS Auto Scaling 기준

## 권장 프로젝트 구조

```text
project/
├── docs/
│   ├── 00_REQUIREMENTS.md
│   ├── 01_ROADMAP.md
│   ├── 02_ARCHITECTURE.md
│   ├── 03_DATABASE.md
│   ├── 04_API_SPEC.md
│   ├── 05_MIGRATION.md
│   ├── 06_DEPLOYMENT.md
│   ├── 07_TROUBLESHOOTING.md
│   ├── 08_PORTFOLIO.md
│   ├── 09_WORK_LOG.md
│   ├── 10_SPRING_BOOT_STUDY.md
│   ├── 11_ONPREM_MANUAL_TEST.md
│   ├── 12_TERRAFORM_AWS_INFRA_SUMMARY.md
│   ├── 13_AWS_DEPLOYMENT_RUNBOOK.md
│   └── 14_FINAL_VALIDATION.md
├── backend/
├── kubernetes/
├── nginx/
├── terraform/
├── README.md
├── AGENTS.md
└── TASKS.md
```

## 문서 역할

- `docs/00_REQUIREMENTS.md`: 서비스 범위, 기능 요구사항, 비기능 요구사항
- `docs/01_ROADMAP.md`: 2주간 세부 일정과 일자별 목표
- `docs/02_ARCHITECTURE.md`: 온프레미스 및 AWS 목표 아키텍처
- `docs/03_DATABASE.md`: ERD, 테이블 정의, 마이그레이션 고려사항
- `docs/04_API_SPEC.md`: 인증/파일/사용자 API 명세
- `docs/05_MIGRATION.md`: MinIO -> S3, PostgreSQL -> Aurora, Redis -> ElastiCache 전환 전략
- `docs/06_DEPLOYMENT.md`: Docker Compose, ECS, Terraform, CI/CD 배포 방식
- `docs/07_TROUBLESHOOTING.md`: 구현 및 운영 중 자주 발생하는 이슈와 해결법
- `docs/08_PORTFOLIO.md`: 프로젝트 소개, 기술 선택 이유, 성과 정리
- `docs/09_WORK_LOG.md`: Day별 실제 작업 내역, 산출물, 검증 결과
- `docs/10_SPRING_BOOT_STUDY.md`: 현재 프로젝트를 예시로 정리한 Spring Boot 학습 교보재
- `docs/11_ONPREM_MANUAL_TEST.md`: Docker Compose 온프레미스 환경 수동 검증 절차
- `docs/12_TERRAFORM_AWS_INFRA_SUMMARY.md`: Terraform 기반 AWS 인프라 설계 1차 정리
- `docs/13_AWS_DEPLOYMENT_RUNBOOK.md`: Terraform 실행, GitHub Actions 배포, AWS 기능 테스트 절차
- `docs/14_FINAL_VALIDATION.md`: 온프레미스와 AWS 최종 비교 검증 절차

## 진행 순서

1. 요구사항 확정
2. 아키텍처 설계
3. 백엔드 기능 구현
4. 온프레미스 배포
5. AWS 인프라 구축
6. ECS 및 저장소 이전
7. CI/CD, 모니터링, 보안 정리
8. 포트폴리오 문서화

## 온프레미스 통합 실행

Day 6 기준 로컬 온프레미스 스택은 Docker Compose로 실행한다.

```bash
docker compose up --build
```

API 진입점:

```text
http://localhost:8080
```

MinIO 콘솔:

```text
http://localhost:9001
```

## AWS 전환 요약

| 온프레미스 | AWS |
| --- | --- |
| Nginx reverse proxy | ALB, Route53, ACM |
| Docker Compose backend | ECS Fargate service |
| PostgreSQL container | Aurora PostgreSQL |
| Redis container | ElastiCache Redis |
| MinIO bucket | S3 bucket |
| backend 중심 파일 처리 | S3 event, Lambda 후처리 |
| 로컬 로그 확인 | CloudWatch Logs/Alarms |

AWS 배포와 검증은 다음 문서를 따른다.

```text
docs/13_AWS_DEPLOYMENT_RUNBOOK.md
docs/14_FINAL_VALIDATION.md
```

## 최종 검증 기준

- 온프레미스와 AWS에서 API 응답 계약이 동일하다.
- 업로드, 다운로드, 삭제 시나리오가 양쪽 환경에서 성공한다.
- DB의 `files.storage_path`와 object storage key가 일치한다.
- Redis/ElastiCache 캐시 키는 `files:metadata:{fileId}` 형식을 유지한다.
- S3 object 생성 후 Lambda 후처리 결과가 object tag와 CloudWatch log에 남는다.
- ECS task 장애, Lambda 오류, ALB 5xx를 CloudWatch와 AWS console에서 추적할 수 있다.

## 포트폴리오 이미지

생성된 SVG 다이어그램은 `docs/assets/`에 있다.

- `docs/assets/onprem-architecture.svg`
- `docs/assets/aws-architecture.svg`
- `docs/assets/file-upload-flow.svg`
- `docs/assets/operations-validation.svg`

실제 AWS 콘솔 캡처가 필요한 항목은 `docs/assets/README.md`의 체크리스트를 따른다.
