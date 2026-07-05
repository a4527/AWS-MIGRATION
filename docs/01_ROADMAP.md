# 01. Roadmap

## 2주 일정 개요

이 로드맵은 10영업일 기준으로 구성한다.

- Week 1: 온프레미스 서비스 완성
- Week 2: AWS 마이그레이션 및 운영 자동화

## Week 1 - 온프레미스 구축

### Day 1: 요구사항 및 설계

- 서비스 범위 확정
- 사용자 역할 정의
- 파일 라이프사이클 정의
- API 초안 작성
- DB 엔티티 초안 작성
- 온프레미스 아키텍처 초안 작성

산출물:

- `docs/00_REQUIREMENTS.md`
- `docs/02_ARCHITECTURE.md` 초안
- `docs/03_DATABASE.md` 초안
- `docs/04_API_SPEC.md` 초안

### Day 2: Spring Boot 프로젝트 골격

- Spring Boot 프로젝트 생성
- 패키지 구조 설계
- 공통 응답 포맷 정의
- 예외 처리 구조 구성
- JWT 보안 설정 골격 작성

산출물:

- 백엔드 기본 구조
- 보안/예외 처리 기본 틀

### Day 3: 인증 및 사용자 관리

- 회원가입 구현
- 로그인 구현
- JWT 발급/검증 구현
- 사용자 조회 API 구현
- 권한 기반 접근 제어 적용

산출물:

- 인증 플로우 동작
- 사용자 관리 API

### Day 4: 파일 CRUD

- 업로드 API 구현
- 다운로드 API 구현
- 삭제 API 구현
- 메타데이터 저장 로직 구현
- 파일 확장자/크기 검증

산출물:

- 파일 공유 핵심 기능
- API 예시 정리

### Day 5: PostgreSQL, Redis, MinIO 연동

- PostgreSQL 테이블 확정
- JPA 엔티티 및 Repository 작성
- Redis 캐시 적용
- MinIO 연동
- 로컬 파일 저장소 대체 확인

산출물:

- `docs/03_DATABASE.md` 완성
- 스토리지 및 캐시 연동 완료

### Day 6: Docker Compose 및 Nginx

- Dockerfile 작성
- Docker Compose 구성
- Nginx reverse proxy 설정
- 환경변수 분리
- 로컬 통합 실행 검증

산출물:

- Docker Compose 기반 실행 환경

### Day 7: 온프레미스 검증 및 문서화

- 기능 테스트
- 장애 시나리오 점검
- API 문서 정리
- 트러블슈팅 초안 작성
- 온프레미스 아키텍처 문서 마무리

산출물:

- 온프레미스 완성본
- API 문서
- ERD
- 아키텍처 문서

## Week 2 - AWS Cloud Migration

### Day 8: AWS 인프라 설계 및 Terraform 시작

- VPC 설계
- ECS, ECR, IAM, ALB, Route53, ACM 범위 정의
- Terraform 프로젝트 구조 생성
- 환경별 변수 체계 설계

산출물:

- `terraform/` 기본 구조
- AWS 인프라 설계 문서

### Day 9: ECS 및 데이터/스토리지 이전

- ECS 클러스터와 Fargate 서비스 배포
- Aurora PostgreSQL 구성
- ElastiCache 계획 반영
- S3 업로드 구조로 전환
- Secrets 및 IAM 정책 정리

산출물:

- AWS 데이터/스토리지 마이그레이션 설계

### Day 10: ECS 배포 및 CI/CD

- ECS task definition 배포 흐름 작성
- GitHub Actions CI/CD 구축
- ECR 빌드/푸시 연동
- ECS service 배포 자동화

산출물:

- CI/CD 파이프라인
- ECS 배포 흐름

### Day 11: Lambda, 모니터링, 스케일링

- S3 이벤트 기반 Lambda 구성
- 바이러스 검사 및 메타데이터 추출 후처리 구현
- CloudWatch 로그/지표 설정
- Auto Scaling 정책 구성
- 헬스체크 및 롤링 배포 확인

산출물:

- 관측성 및 확장성 구성

### Day 12: HTTPS 및 보안 강화

- Route53 도메인 연결
- ACM 인증서 적용
- ALB HTTPS 구성
- IAM 최소 권한 원칙 점검
- 공개/비공개 리소스 분리 확인

산출물:

- 보안 강화된 운영 환경

### Day 13: 최종 검증 및 성능 점검

- 온프레미스 vs AWS 동작 비교
- 업로드/다운로드 시나리오 테스트
- 장애 복구 시나리오 테스트
- 로그 및 알람 확인

산출물:

- 최종 시스템 아키텍처
- 검증 결과 요약

### Day 14: 포트폴리오 및 마감

- README 정리
- 프로젝트 스토리 작성
- 기술 선택 이유 정리
- 결과 이미지/다이어그램 정리
- `docs/08_PORTFOLIO.md` 완성

산출물:

- 포트폴리오 문서
- 최종 제출본

## 일정 리스크

- AWS 인프라 생성 권한 부족
- ECS/ALB 구성 시간 지연
- S3/Aurora 전환 중 데이터 불일치
- CI/CD와 보안 정책 조정 지연

## 우선순위

1. 파일 업로드/다운로드 핵심 기능
2. 인증/권한
3. 온프레미스 안정화
4. AWS 마이그레이션
5. CI/CD 및 관측성
6. 포트폴리오 정리
