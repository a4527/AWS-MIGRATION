문서는 `docs/`의 대문자 `.md` 파일에 둔다. 루트에는 `AGENTS.md`·`README.md`·`CLAUDE.md`를 두고, 컴포넌트 README는 실행·코드 이해의 진입점으로 유지한다. `CLAUDE.md`는 이 문서로 연결하며 공통 규칙을 반복하지 않는다. 작업에 필요한 관련 절만 읽는다.

## 문서 책임

| 질문 | 기준 문서 |
| --- | --- |
| 무엇을 제공하고 어떤 조건을 지키는가 | [PRD](docs/PRD.md) |
| 무엇이 남았고 무엇을 확인해야 완료인가·진행 순서는 무엇인가 | [TASKS](docs/TASKS.md) |
| 현재 어디까지 구현·검증했는가 | [PROGRESS](docs/PROGRESS.md) |
| 구조·책임·데이터 흐름은 무엇인가 | [DESIGN](docs/DESIGN.md) |
| 데이터·API 계약은 무엇인가 | [DATABASE](docs/design/DATABASE.md), [API](docs/design/API.md) |
| 어떻게 이전·배포·검증하는가 | [MIGRATION](docs/design/MIGRATION.md), [DEPLOYMENT](docs/design/DEPLOYMENT.md), [VALIDATION](docs/design/VALIDATION.md) |
| AWS 리소스의 구체적인 구성과 한계는 무엇인가 | [AWS](docs/design/AWS.md) |
| 무엇을 왜 결정했고 무엇이 유효한가 | [DECISIONS](docs/DECISIONS.md) |
| 그날 무엇을 어떤 조건에서 확인했는가 | [현재 작업일지](docs/worklogs/2026-11-17.md), [기존 Day 이력](docs/worklogs/LEGACY.md) |
| 문제를 어떻게 진단·해결·재검증하는가 | [TROUBLESHOOTING](docs/TROUBLESHOOTING.md) |
| 사용자에게 의미 있게 바뀐 것은 무엇인가 | [CHANGELOG](docs/CHANGELOG.md) |
| 어떻게 설치·실행하는가 | [README](README.md), [backend](backend/README.md), [terraform](terraform/README.md) |
| 외부에 어떤 근거로 설명하는가 | [PORTFOLIO](docs/PORTFOLIO.md) |
| 무엇이 아직 미승인 제안인가 | [제안 목록](docs/proposals/INDEX.md) |
| 이전 문서 체계(Day 1~14)는 무엇이었나 | [이전 문서 요약](docs/LEGACY.md), git 태그 `legacy-docs-2026-11-17` |

## 갱신 규칙

- 제품 약속은 PRD, 구현 계약은 DESIGN과 필요한 `docs/design/` 문서, 선택 이유는 DECISIONS에 둔다. 다른 문서에는 짧은 요약·ID·상대 링크만 남긴다. 같은 역할의 기준 문서를 추가하지 않는다.
- 기능은 사용자·운영 관점을 함께 설명하고, API는 요청/응답·오류 코드를 포함한다. 구성요소 책임과 데이터 흐름, 로컬·온프레미스와 AWS 조건을 구분한다. 구현 코드는 `backend/`, `terraform/`에 둔다. Kubernetes 채택 시 해당 코드는 `kubernetes/`에 둔다.
- 변경은 요구사항 → 구조 → 데이터 → API → 배포 → 운영 순서로 관련 기준부터 반영한다. 마이그레이션 변경은 MIGRATION을 함께 갱신한다. 영향받는 TASK 수용 기준·결정 연결을 맞춘다.
- 승인된 미구현 설계·현재 구현·미정을 구분한다. 코드와 설계의 차이가 승인 계약을 자동으로 바꾸지 않는다. 검증 범위 밖을 완료로 표시하지 않는다. 작업 ID를 보존한다. 이전 Day별 완료 표기는 [이전 문서 요약](docs/LEGACY.md)과 Day 이력에 보존하고 현재 문서에 다시 쓰지 않는다.
- 중요한 새 결정이나 기존 결정 변경·충돌은 근거·대안·영향을 논의하고 명시적 동의 후 반영한다. 기존 승인 범위의 구현·문구 정리는 다시 승인받지 않는다.
- DECISIONS는 ID·결정일·상태·대체 관계를 관리한다. 작은 결정은 결론·핵심 이유를 짧게 적고, 큰 전환 비용·핵심 약속·보안/데이터 경계·중요 절충은 합의 후 `docs/adr/NNN-TITLE.md`에 기록한다. 역사적 판단을 보존하고 후속 결정에 대체 범위·근거를 연결한다.
- 현재 상태는 PROGRESS, 사용자 기능·동작 변경은 CHANGELOG, 실제 조건·명령·결과·한계는 수행일의 `docs/worklogs/YYYY-MM-DD.md`에 둔다. 같은 날짜는 보완하고 작업하지 않은 날짜의 기록은 만들지 않는다. 날짜가 미확인인 기존 Day 이력은 LEGACY에 보존한다.
- 미승인 비교·대안은 필요할 때 `docs/proposals/TOPIC.md`에 문제·근거·대안·영향·미정·다음 확인을 적고 제안 목록에 연결한다. 채택 시 기준 문서·결정과 남은 미승인 범위를 연결한다. 내용 없는 ADR·상세 설계·안내 파일을 미리 만들지 않는다.
- 종료 시 변경 파일과 직접 연결된 문서의 요구사항·완료 기준·결정·상태·근거·링크를 대조하고 결과·불일치를 당일 작업일지에 남긴다. 외부 출처의 관찰·추정·미확인과 의도적 시험 장애를 구분한다. 비밀값·계정·개인 데이터 원문을 기록하지 않는다.
- 문서 번호와 파일명을 반복하는 첫 H1 제목을 붙이지 않는다. 필요한 내용별 제목과 실행 절차 번호는 유지한다. 탐색·작성 규칙은 이 파일 한 곳에서 관리하고 별도 README·DOCUMENTATION·ROADMAP으로 복제하지 않는다.
