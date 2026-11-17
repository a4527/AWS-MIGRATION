2026-11-17 문서 재구성 이전의 문서 체계를 요약한다. 현재 기준은 각 기준 문서를 따르며, 이 문서의 내용은 현재 계약이나 완료 증빙이 아니다.

## 원문 보관 위치

- 이전 문서 원문은 로컬 git 태그 `legacy-docs-2026-11-17`(커밋 `42db7d8`)에 고정했다. 태그는 원격 저장소에 push하지 않았다.
- 원문 보기: `git show legacy-docs-2026-11-17:docs/design/VALIDATION.md`
- 문서 목록: `git ls-tree -r --name-only legacy-docs-2026-11-17 docs`
- 2026-11-16~17 작업일지의 IAC-02, CICD-01, LAYER-01/02, FILE-01/02/04/05 내용은 재구성 후에도 현재 기준 문서에 반영되어 있어 legacy로 분류하지 않았다.

## 이전 진행 방식

- 2주 일정(Day 1~14)으로 온프레미스 구현(1주차)과 AWS 전환(2주차)을 계획하고 진행했다. 상세 일정과 Day별 결정·작업 기록은 [Day 이력](worklogs/LEGACY.md)에 그대로 보존한다.
- TASKS의 Day별 `[o]` 표기는 산출물 작성 완료 표기였다. 실제 AWS 배포·비교 검증·장애 복구·성능 측정의 실행 성공을 보증하지 않는다. Day 13은 검증 절차 작성까지였다.
- 2026-11-16에 포트폴리오 보강 작업 15개와 선택 검토 1개(PORT·IAC·SEC·CICD·OBS·OPS·PERF·ARCH·K8S·VERIFY)를 등록했다. 작업 ID와 수용 기준은 재구성된 [TASKS](TASKS.md)로 옮겼다.

| Day | 당시 산출물 |
| --- | --- |
| 1 | 요구사항, 도메인·권한 모델, 파일 라이프사이클, API·ERD·온프레미스 아키텍처 초안 |
| 2 | Spring Boot 골격, 패키지 구조, 공통 응답·예외, JWT 보안 골격 |
| 3 | 회원가입·로그인·JWT, 사용자 조회, 권한 기반 접근 제어 |
| 4 | 업로드·다운로드·삭제 API, 메타데이터 저장, 확장자·크기 검증 |
| 5 | PostgreSQL/JPA, Redis 캐시, MinIO 연동 |
| 6 | Dockerfile, Docker Compose, Nginx, 환경변수 분리 |
| 7 | 온프레미스 수동 기능·장애 점검, API·트러블슈팅 문서 |
| 8 | VPC 설계, Terraform 구조와 변수 체계 |
| 9 | ECS Fargate, Aurora, ElastiCache, S3 전환 구성 |
| 10 | GitHub Actions CI/CD, ECR push, ECS 배포 자동화 |
| 11 | S3 이벤트 Lambda, CloudWatch 로그·알람, Auto Scaling |
| 12 | Route53·ACM·ALB HTTPS(선택형), IAM 최소 권한 점검 |
| 13 | 온프레미스·AWS 비교, 장애 복구, 로그·알람 검증 절차 작성 |
| 14 | README, 포트폴리오 문서와 이미지 |

## 문서별 이전 내용과 처리

| 문서 | 이전 내용 | 재구성 후 |
| --- | --- | --- |
| README | 2주 마이그레이션 완료 프로젝트로 소개 | 현재 코드 기준 소개와 단계별 진행 안내로 교체 |
| PRD | 요구사항 + "Day 1 기준" 절 | 요구사항 유지, Day 절 제거 |
| DESIGN | 구조 + Day 7~9 기준 절 | 현재 구조·흐름만 남기고 Day 절 제거 |
| TASKS | Day 1~14 체크리스트 + 후속·보강 작업 | 단계별(준비 → 온프레미스 → 마이그레이션 → 운영 검증 → 정리) 재구성, 기존 작업 ID 유지 |
| PROGRESS | Day 이력 중심 진행 요약 | 새 단계 기준 현재 상태로 교체 |
| PORTFOLIO | 1·2주차 스토리와 완료 기준 설명 | 현재 설계 포인트와 증빙 수집 계획으로 교체. 새 실행 결과로 PORT-02에서 완성 |
| API | 계약 + Day 1·3·4·7 기준 절 | 계약 유지, 미구현 엔드포인트 표기 정리, Day 절 제거 |
| DATABASE | 스키마 + Day 1·5 기준 절 | 스키마 유지, 테이블별 구현 상태 표기, Day 절 제거 |
| DEPLOYMENT | 로컬·온프레미스·AWS 배포 + Day별 설명 | 로컬 호스트 준비 → 온프레미스 구축 → AWS 배포 런북으로 재작성 |
| MIGRATION | 전략 + Day 2~14 기준 절 | 현재 코드 기준 전환 절차와 데이터 이전 결정 항목으로 재작성 |
| VALIDATION | Day 7 온프레미스 수동 점검 17단계, Day 13 온프레미스·AWS 비교 절차 | 시나리오 ID 기반으로 재작성. 멱등성·장애 주입·관리자 점검·데이터 이전 검증 추가 |
| AWS | Terraform 리소스 해설 | 유지. Day 표현만 정리 |

## 이전 검증 절차 요약

- Day 7 온프레미스 점검: Compose 기동 → health → 회원가입·로그인 → 사용자 조회·수정 → 업로드·목록·단건·다운로드 → PostgreSQL·Redis·MinIO 직접 확인 → 삭제 → 실패 케이스(인증 누락·확장자·권한) → backend·PostgreSQL·Redis·MinIO 컨테이너 중지 점검. 당시 Redis 중지 시 API 오류가 날 수 있다고 기록했으며 FILE-02로 해소했다.
- Day 13 비교 절차: 같은 API 시나리오를 온프레미스와 AWS에서 실행해 응답 계약, `files.storage_path`와 object key, 캐시 키 형식, S3 object tag·Lambda 로그, ECS task 중지 복구, Lambda 실패 주입, CloudWatch 알람, smoke 성능을 비교하는 절차였다. 이 중 AWS 절차는 현재 [검증 기준](design/VALIDATION.md)의 AWS 시나리오로 옮겼다.
- 위 절차의 실제 실행 결과는 [Day 이력](worklogs/LEGACY.md)의 해당 Day 기록 범위만 유효하다.
