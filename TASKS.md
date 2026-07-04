# TASKS.md

## Day 1 - 요구사항 및 설계

- [o] 요구사항 정리
- [o] 서비스 도메인 및 권한 모델 정의
- [o] 파일 라이프사이클 정의
- [o] API 초안 작성
- [o] PostgreSQL 스키마 및 ERD 초안 작성
- [o] 온프레미스 아키텍처 초안 작성

## Day 2 - Spring Boot 프로젝트 골격

- [o] Spring Boot 프로젝트 생성
- [o] 패키지 구조 설계
- [o] 공통 응답 포맷 정의
- [o] 예외 처리 구조 구성
- [o] JWT 보안 설정 골격 작성

## Day 3 - 인증 및 사용자 관리

- [o] 회원가입 구현
- [o] 로그인 구현
- [o] JWT 발급/검증 구현
- [o] 사용자 조회 API 구현
- [o] 권한 기반 접근 제어 적용

## Day 4 - 파일 CRUD

- [o] 업로드 API 구현
- [o] 다운로드 API 구현
- [o] 삭제 API 구현
- [o] 메타데이터 저장 로직 구현
- [o] 파일 확장자/크기 검증

## Day 5 - PostgreSQL, Redis, MinIO 연동

- [o] PostgreSQL 테이블 확정
- [o] JPA 엔티티 및 Repository 작성
- [o] Redis 캐시 적용
- [o] MinIO 연동
- [o] 로컬 파일 저장소 대체 확인

## Day 6 - Docker Compose 및 Nginx

- [o] Dockerfile 작성
- [o] Docker Compose 구성
- [o] Nginx reverse proxy 설정
- [o] 환경변수 분리
- [o] 로컬 통합 실행 검증

## Day 7 - 온프레미스 검증 및 문서화

- [o] 기능 테스트
- [o] 장애 시나리오 점검
- [o] API 문서 정리
- [o] 트러블슈팅 초안 작성
- [o] 온프레미스 아키텍처 문서 마무리

## Day 8 - AWS 인프라 설계 및 Terraform 시작

- [ ] VPC 설계
- [ ] EKS, ECR, IAM, ALB, Route53, ACM 범위 정의
- [ ] Terraform 프로젝트 구조 생성
- [ ] 환경별 변수 체계 설계

## Day 9 - EKS 및 데이터/스토리지 이전

- [ ] EKS 클러스터 배포
- [ ] Aurora PostgreSQL 구성
- [ ] ElastiCache 계획 반영
- [ ] S3 업로드 구조로 전환
- [ ] Secrets 및 IAM 정책 정리

## Day 10 - Kubernetes 배포 및 CI/CD

- [ ] Kubernetes 매니페스트 작성
- [ ] GitHub Actions CI/CD 구축
- [ ] ECR 빌드/푸시 연동
- [ ] EKS 배포 자동화

## Day 11 - Lambda, 모니터링, 스케일링

- [ ] S3 이벤트 기반 Lambda 구성
- [ ] 바이러스 검사 및 메타데이터 추출 후처리 구현
- [ ] CloudWatch 로그/지표 설정
- [ ] Auto Scaling 정책 구성
- [ ] 헬스체크 및 롤링 배포 확인

## Day 12 - HTTPS 및 보안 강화

- [ ] Route53 도메인 연결
- [ ] ACM 인증서 적용
- [ ] ALB HTTPS 구성
- [ ] IAM 최소 권한 원칙 점검
- [ ] 공개/비공개 리소스 분리 확인

## Day 13 - 최종 검증 및 성능 점검

- [ ] 온프레미스 vs AWS 동작 비교
- [ ] 업로드/다운로드 시나리오 테스트
- [ ] 장애 복구 시나리오 테스트
- [ ] 로그 및 알람 확인

## Day 14 - 포트폴리오 및 마감

- [ ] README 정리
- [ ] 프로젝트 스토리 작성
- [ ] 기술 선택 이유 정리
- [ ] 결과 이미지/다이어그램 정리
- [ ] `docs/08_PORTFOLIO.md` 완성
