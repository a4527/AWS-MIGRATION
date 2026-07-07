# Portfolio Assets

포트폴리오와 발표 자료에 바로 사용할 수 있는 생성 이미지와 다이어그램을 모아 둔다.

## 생성된 다이어그램

- `onprem-architecture.svg`: Docker Compose 기반 온프레미스 구조
- `aws-architecture.svg`: AWS 목표 구조와 subnet/managed service 배치
- `file-upload-flow.svg`: 업로드, Aurora metadata, S3 object, Lambda 후처리 흐름
- `operations-validation.svg`: 최종 운영 검증 기준 요약

## 실제 콘솔 캡처가 필요한 이미지

아래 항목은 실제 리소스 상태를 보여줘야 하므로 AWS Console 또는 CLI 결과 화면으로 캡처한다.

- ECS service running/desired count와 deployment 상태
- ALB target group health 상태
- S3 object와 object tag 화면
- Lambda Monitor 또는 CloudWatch log stream
- CloudWatch alarm overview
- ECS CPU, memory, network metric 화면

## 권장 배치 순서

1. `onprem-architecture.svg`
2. `aws-architecture.svg`
3. `file-upload-flow.svg`
4. API smoke test 또는 Docker Compose 실행 캡처
5. ECS/ALB 상태 캡처
6. S3/Lambda 후처리 캡처
7. CloudWatch alarm/metric 캡처
8. `operations-validation.svg`
