# Portfolio Assets

포트폴리오와 발표 자료에 사용하는 다이어그램을 모아 둔다.

## 대표 이미지

- `portfolio-springboot-flow.png`: Spring Boot 요청 처리 흐름과 Clean Architecture 계층 구조
- `portfolio-docker-compose.png`: Docker Compose 기반 온프레미스 전체 구조
- `portfolio-aws-architecture.png`: Terraform 기준 AWS 아키텍처 단순화 다이어그램

## 보조 SVG

아래 SVG는 문서 보조용으로 유지한다.

- `onprem-architecture.svg`: Docker Compose 기반 온프레미스 구조 요약
- `aws-architecture.svg`: AWS 목표 구조 요약
- `file-upload-flow.svg`: 업로드, Aurora metadata, S3 object, Lambda 후처리 흐름
- `operations-validation.svg`: 최종 운영 검증 기준 요약

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
