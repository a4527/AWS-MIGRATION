사용자 기능·동작 변경을 기록한다. 확인 가능한 릴리스별 변경 근거가 없어 과거 이력을 소급 작성하지 않았다. 문서 정리와 작업 등록의 수행 근거는 [작업일지](worklogs/2026-11-16.md), 현재 범위는 [PROGRESS](PROGRESS.md)를 따른다.

## 2026-11-23 (미배포)

- 백엔드를 Spring Boot 4.1.3(Spring Framework 7.0.11, Spring Security 7.1.3, Hibernate 7.4, Jackson 3, Tomcat 11)으로 올렸다. spring-webmvc 취약점 2건(CVE-2026-47884·47890)의 예외가 필요 없어졌다. API 요청·응답 형식과 오류 코드는 바뀌지 않았다.
- 근거: [작업일지](worklogs/2026-11-23.md#sec-02-spring-boot-4-전환)

## 2026-11-20 (미배포)

- 백엔드 의존성을 보안 패치 버전으로 올렸다(Spring Boot 3.3.7 → 3.5.16, Tomcat 10.1.62, Netty 4.1.141, Jackson 2.21.8, PostgreSQL JDBC 42.7.14, MinIO 8.6.0, AWS SDK 2.59.5 등). API 동작은 바뀌지 않았다.
- 10MiB를 넘는 업로드가 새 Tomcat에서 `FILE_TOO_LARGE` 응답 대신 연결 끊김으로 실패하지 않도록 Tomcat 본문 버림 한도를 multipart 요청 한도에 맞췄다.
- 근거: [작업일지](worklogs/2026-11-20.md#cicd-자동-배포와-trivy-수정)

## 2026-11-17 (미배포)

- `POST /api/files`가 선택 헤더 `Idempotency-Key`를 받는다. 같은 키로 다시 보내면 파일을 새로 만들지 않고 첫 결과를 `Idempotent-Replayed: true`와 함께 돌려준다. 새 오류 코드 `IDEMPOTENCY_IN_PROGRESS`(409), `IDEMPOTENCY_KEY_MISMATCH`(422)를 추가했다.
- 업로드 중 DB 기록이 실패하면 저장한 파일 객체를 정리한다. 관리자용 `GET /api/admin/storage/orphans`, `POST /api/admin/storage/orphans/cleanup`으로 남은 객체를 찾고 지울 수 있다.
- Redis가 응답하지 않아도 파일 조회·다운로드·삭제가 동작한다. 다운로드와 삭제는 항상 DB 상태를 기준으로 판단한다.
- 근거: [작업일지](worklogs/2026-11-17.md#file-01020405-구현), [ADR-001](adr/001-UPLOAD-IDEMPOTENCY-KEY.md)
- 1MB를 넘고 10MiB 이하인 파일 업로드가 `INTERNAL_SERVER_ERROR`(500)로 실패하던 문제를 고쳤다. 한도를 넘는 업로드는 `FILE_TOO_LARGE`(400)로 응답한다.
- Redis가 멈췄을 때 요청이 최대 60초씩 지연되던 문제를 고쳤다(Redis 명령·연결 타임아웃 500ms).
