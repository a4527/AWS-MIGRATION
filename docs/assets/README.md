포트폴리오와 발표 자료에 사용하는 다이어그램을 모아 둔다.

## 대표 이미지

아래 PNG는 2026-11-17 문서 재구성 이전에 만든 이미지다. 구성은 현재 코드·Terraform과 같지만 새 진행 단계의 실행 증빙은 아니다. 현재 코드 기준 그림은 `architecture-overview.html`을 쓴다.

- `portfolio-springboot-flow.png`: Spring Boot 요청 처리 흐름과 Clean Architecture 계층 구조
- `portfolio-docker-compose.png`: Docker Compose 기반 온프레미스 전체 구조
- `portfolio-aws-architecture.png`: Terraform 기준 AWS 아키텍처 단순화 다이어그램

## 보조 SVG

아래 SVG는 문서 보조용으로 유지한다.

- `onprem-architecture.svg`: Docker Compose 기반 온프레미스 구조 요약
- `aws-architecture.svg`: AWS 목표 구조 요약
- `file-upload-flow.svg`: 업로드, Aurora metadata, S3 object, Lambda 후처리 흐름
- `operations-validation.svg`: 최종 운영 검증 기준 요약

## 아키텍처 레퍼런스 페이지

- `architecture-overview.html`: 온프레미스·AWS 구성도, 패키지·계층 구조, 계층 의존성, 프로필 조합, 업로드 흐름, Terraform 모듈 의존성, CI/CD를 한 페이지로 정리한 브라우저용 문서. 2026-11-17 작업 트리 기준 요약이며 구조·책임 기준은 [DESIGN](../DESIGN.md)을 따른다.

## 권장 배치 순서

1. `portfolio-springboot-flow.png`
2. `portfolio-docker-compose.png`
3. `portfolio-aws-architecture.png`
4. AWS Console 검증 캡처
5. CloudWatch alarm/metric 캡처

## 실제 콘솔 캡처가 필요한 이미지

아래 항목은 실제 리소스 상태를 보여줘야 하므로 AWS Console 또는 CLI 결과 화면으로 캡처한다.

- ECS service running/desired count와 deployment 상태
- ALB target group health 상태
- S3 object와 object tag 화면
- Lambda Monitor 또는 CloudWatch log stream
- CloudWatch alarm overview
- ECS CPU, memory, network metric 화면
