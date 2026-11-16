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
