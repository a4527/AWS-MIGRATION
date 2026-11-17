## 기존 기록 탐색

기존 결정의 원문과 당시 맥락은 [Day별 작업일지](worklogs/LEGACY.md)의 `결정 사항` 절에 보존한다. 아래 목록은 탐색용이며 새 승인이나 ADR을 소급 생성한 것이 아니다. 개별 승인일·승인 근거는 이번 정리에서 확인하지 않았다.

| 주제 | 당시 기록 | 현행 기준·한계 |
| --- | --- | --- |
| 바이너리/메타데이터 분리, 논리 삭제, 저장소 추상화 | [Day 1](worklogs/LEGACY.md#day-1---요구사항-및-설계) | [요구사항](PRD.md), [데이터 모델](design/DATABASE.md) |
| OIDC 인증과 ECS rolling deployment | [Day 10](worklogs/LEGACY.md#day-10---ecs-배포-및-cicd) | [배포 기준](design/DEPLOYMENT.md) |
| 확장자 가드·S3 tag, DB 동기화와 알림 action의 후속 분리 | [Day 11](worklogs/LEGACY.md#day-11---lambda-모니터링-스케일링) | [인프라 해설](design/AWS.md) |
| 선택형 HTTPS, S3 prefix 공유와 IAM 제한 | [Day 12](worklogs/LEGACY.md#day-12---https-및-보안-강화) | [마이그레이션](design/MIGRATION.md) |
| 최종 검증 절차 작성과 실제 AWS 실행의 분리 | [Day 13](worklogs/LEGACY.md#day-13---최종-검증-및-성능-점검) | [검증 절차](design/VALIDATION.md), [현재 상태](PROGRESS.md) |

기술 선택 이유를 설명하는 자료는 [포트폴리오](PORTFOLIO.md#기술-선택-이유)에서 찾는다. 설명 자료를 승인 증빙으로 취급하지 않는다.

## 새 결정 기록

- 작은 결정은 이 문서에 충돌 없는 ID, 결정일, 상태, 결론·이유, 합의 근거와 기준 문서 링크를 기록한다.
- 큰 전환 비용·사용자 약속·보안/데이터 경계의 중요한 결정은 실제 합의 후 `adr/NNN-TITLE.md`에 기록하고 이 목록에 연결한다.
- 미승인 대안은 `proposals/TOPIC.md`에 둔다. Accepted는 명시적 합의 근거가 있을 때만 사용한다.
- 후속 결정에는 대체 ID·범위를 적고 Superseded 또는 Partially Superseded를 구분한다. 당시 배경·절충을 현재 구현으로 덮어쓰지 않는다.
- 필수 작성 항목은 [문서 관리](../AGENTS.md#갱신-규칙)를 따른다.

## 결정 목록

| ID | 결정일 | 상태 | 결론·이유 | 합의 근거·기준 문서 |
| --- | --- | --- | --- | --- |
| DEC-001 | 2026-11-17 | Accepted | 업로드 멱등성 키를 DB `upload_requests`에 저장한다. 업로드가 이미 DB에 의존하므로 새 가용성 의존성이 없고 유니크 제약으로 동시 요청을 막는다. | [ADR-001](adr/001-UPLOAD-IDEMPOTENCY-KEY.md) |
| DEC-002 | 2026-11-17 | Accepted | 업로드는 객체를 먼저 쓰고 DB 기록을 짧게 재시도한다. 기록 실패가 확실할 때만 객체를 보상 삭제하고, 결과를 알 수 없거나 보상이 실패하면 객체를 남겨 관리자 정합성 점검으로 찾는다. 결과 불명 상태에서 지우면 실제로 저장된 파일을 잃을 수 있기 때문이다. | 2026-11-17 대화 합의(객체 우선 저장·다양한 실패 고려 요청, 멱등성 구현 승인), [구조](DESIGN.md#파일-업로드-흐름), [API](design/API.md#관리자-저장소-점검) |
| DEC-003 | 2026-11-17 | Accepted | 캐시는 메타데이터 단건 조회에만 쓰고 다운로드·삭제는 DB로 판단한다. Redis 오류는 DB로 우회한다. 삭제 상태는 즉시 재시도 후 백그라운드 재시도로 캐시에 반영하고 끝내 실패하면 운영자용 로그·지표를 남긴 채 삭제는 성공으로 응답한다. 쓰기는 `updatedAt` 버전 비교로 오래된 값을 거부한다. 사용자 알림은 사용자가 조치할 수 없어 쓰지 않는다. | 2026-11-17 대화에서 사용자가 권장안 진행을 승인, [구조](DESIGN.md#캐시-일관성) |
| DEC-004 | 2026-11-17 | Accepted | 문서를 현재 코드 기준으로 재구성하고 이전 체계는 `docs/LEGACY.md` 요약과 git 태그 `legacy-docs-2026-11-17`로 보존한다. TASKS는 단계별(준비 → 온프레미스 → AWS 마이그레이션 → 운영 검증 → 정리)로 다시 짜고 기존 작업 ID는 유지한다. 온프레미스는 현재 로컬 PC의 WSL2에서 Docker Compose로 운영하고, 마이그레이션 대상은 현재 Terraform 구성이다. | 2026-11-17 대화에서 사용자가 legacy 보관 방식(요약+태그), 단계별 재구성, 현재 Terraform 유지, 로컬 진행을 선택. [LEGACY](LEGACY.md), [TASKS](TASKS.md) |
| DEC-005 | 2026-11-17 | Accepted | 온프레미스 MinIO 이미지를 `bitnamilegacy/minio:2025.4.22`, 검증용 클라이언트를 `bitnamilegacy/minio-client:2025.4.16`으로 바꾼다. 공식 `minio/minio`·`quay.io/minio` 이미지를 더 이상 받을 수 없고, 이 보관 이미지는 원래 고정한 버전과 같아 재현성이 유지된다. 보안 업데이트가 없으므로 로컬 전용으로만 쓴다. | 2026-11-17 대화에서 사용자가 Chainguard·다른 S3 호환 저장소 대신 선택. [작업일지](worklogs/2026-11-17.md#온프레미스-구축과-검증) |
