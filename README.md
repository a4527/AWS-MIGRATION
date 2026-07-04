# Enterprise File Sharing Platform Modernization

Docker Compose 기반 온프레미스 파일 공유 플랫폼을 먼저 구축하고, 이후 AWS(EKS, Lambda, Aurora, S3 등)로 마이그레이션하는 2주 프로젝트입니다.

## 목표

- Week 1: 온프레미스 환경을 Docker Compose로 구현
- Week 2: AWS 클라우드 네이티브 구조로 전환
- 최종 산출물: 온프레미스/클라우드 아키텍처, API 문서, ERD, Terraform, CI/CD, 포트폴리오 문서

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
│   └── 09_WORK_LOG.md
├── backend/
├── terraform/
├── kubernetes/
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
- `docs/06_DEPLOYMENT.md`: Docker Compose, Kubernetes, Terraform, CI/CD 배포 방식
- `docs/07_TROUBLESHOOTING.md`: 구현 및 운영 중 자주 발생하는 이슈와 해결법
- `docs/08_PORTFOLIO.md`: 프로젝트 소개, 기술 선택 이유, 성과 정리
- `docs/09_WORK_LOG.md`: Day별 실제 작업 내역, 산출물, 검증 결과
- `docs/10_SPRING_BOOT_STUDY.md`: 현재 프로젝트를 예시로 정리한 Spring Boot 학습 교보재
- `docs/11_ONPREM_MANUAL_TEST.md`: Docker Compose 온프레미스 환경 수동 검증 절차

## 진행 순서

1. 요구사항 확정
2. 아키텍처 설계
3. 백엔드 기능 구현
4. 온프레미스 배포
5. AWS 인프라 구축
6. EKS 및 저장소 이전
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
