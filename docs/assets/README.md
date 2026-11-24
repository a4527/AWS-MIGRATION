포트폴리오와 발표 자료에 쓰는 그림·근거 자료를 둔다.

## 정리 페이지 (브라우저로 열기)

- `architecture-overview.html`: 애플리케이션·온프레미스 구조. Compose 구성과 실행 흐름, 도메인별 패키지·계층, 계층 의존 방향, 요청 처리(필터·JWT·컨트롤러·서비스·포트·어댑터), 프로필별 빈 선택, 업로드 흐름. 구조·책임의 기준은 [DESIGN](../DESIGN.md)이다.
- `aws-migration.html`: AWS 이전 기록. 온프레미스→AWS 대응, AWS 구성도, 이전 단계, IaC·CI/CD, 찾아 고친 결함, 보안, 관측, 회복탄력성·부하, 비용·설계 비교(ARCH-01), 증빙. 콘솔 캡처를 WebP로 파일 안에 넣어 이 파일 하나만으로 열린다.
- `aws-architecture.png`: `aws-migration.html`의 AWS 구성도를 PNG로 렌더링한 것. 루트 README와 포트폴리오에서 쓴다.

두 페이지는 claude.ai 아티팩트(「File Share API 아키텍처」, 「File Share AWS 이전 기록」)로도 게시했다. 2026-11-24에 내용이 오래된 SVG 4개와 PNG 3개(`portfolio-*.png`, `*-architecture.svg` 등)를 지우고 두 페이지로 합쳤다.

## 콘솔 캡처

실제 리소스 화면은 `captures/`에 둔다. 캡처 ID·파일명·콘솔 위치·시점·상태는 [포트폴리오 화면 캡처 목록](../PORTFOLIO.md#화면-캡처-목록)에서 관리한다. `aws-migration.html`의 내장 이미지는 이 원본에서 만든다.

## 측정 원본

- `perf-01/`: PERF-01 부하 시험 원본(분 단위 지표 CSV, 타임라인 CSV, k6 로그).
- `arch-01/`: ARCH-01·PERF-02 전후 측정(NAT·ALB 분 단위 지표, 변경 후 k6 요약, S3 경로·task 중지 측정 로그).
