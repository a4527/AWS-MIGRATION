기준일: 2026-11-16 (Asia/Seoul).

## 현재 구현·검증 범위

- Spring Boot 인증·사용자·파일 API와 PostgreSQL/Redis/MinIO/Nginx Compose 실행 구성이 있다. 과거 수행 근거는 [Day 이력](worklogs/LEGACY.md)에 보존한다.
- Terraform에 ECS/Aurora/ElastiCache/S3, Lambda, CloudWatch, 선택형 HTTPS와 OIDC 배포 구성이 있다. 코드의 존재와 실제 AWS 배포 성공은 구분한다.
- Day 1~14의 기존 완료 표시는 산출물 이력이다. 특히 Day 13은 검증 절차 작성이며 실제 AWS 비교·장애 복구·성능 측정 완료를 뜻하지 않는다.
- 실제 백신·후처리 DB 동기화, 비밀값 주입·알림 전달 등 운영 한계는 [AWS 상세](design/AWS.md#현재-한계)를 따른다.

## 다음 작업

[작업 목록](TASKS.md#포트폴리오-보강-작업)의 PORT-01로 AWS 환경·예산·실험 조건을 준비한 뒤 VERIFY-01을 수행한다. IAC-02와 CICD-01은 독립 착수 가능하다. 작업별 의존성·수용 기준·진행 순서는 TASKS 한 곳에서 관리한다.

## 최근 변경과 한계

포트폴리오 핵심 보강 15개와 선택 검토 1개를 등록하고 문서를 역할별로 통합·이동했다. 후속 CICD-01에서 PR 테스트·이미지 빌드·취약점 검사와 배포 job의 AWS 권한 분리를 구현했다. 백엔드 테스트 9개와 workflow 정적 검증을 통과했으며 GitHub Actions에서 이미지 검사·실패 차단을 실제 실행한 증빙은 아직 없다. Compose·Terraform apply·AWS 배포는 수행하지 않았다. 상세 기록은 [당일 작업일지](worklogs/2026-11-16.md)를 참조한다.
