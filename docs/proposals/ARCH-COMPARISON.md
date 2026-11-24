- 상태: 비교 중. 다섯 항목 모두 현재 구성 유지 또는 개선 후보로 정리했고, 개선 3건(S3 gateway endpoint·deregistration delay·목록 페이지 처리)을 2026-11-24 실습에서 전후 측정한다. 기술 교체는 확정하지 않았다
- 작성일: 2026-11-24
- 관련: [ARCH-01](../TASKS.md#4단계---비교정리), [PERF-01](../TASKS.md#3단계---운영-검증보강), [AWS 구성](../design/AWS.md), [포트폴리오 비용](../PORTFOLIO.md#12-비용)

## 문제

현재 AWS 구성은 공유 계정·월 예산 $10·1인 운영이라는 제약에 맞춘 저비용 실습 구성이다([DEC-008](../DECISIONS.md#결정-목록)). 다음 다섯 가지 선택이 요구사항·장애 영향·비용 면에서 맞는지, 가용성을 높이려면 무엇을 얼마에 바꿔야 하는지를 근거와 함께 정리한 적이 없다.

1. 실행 환경: ECS Fargate vs EKS
2. DB: Aurora Serverless v2 vs RDS PostgreSQL
3. 외부 송신: NAT 1개 vs AZ별 NAT vs Regional NAT gateway
4. S3 경로: endpoint 없음(NAT 경유) vs gateway endpoint vs interface endpoint
5. 업로드 전송: ECS 중계 vs presigned URL

## 근거 (현재 구성과 산정 조건)

- 현재 구성(관찰, Terraform 기본값·tfvars 예시): AZ 2개(us-east-1a·1b), NAT 1개(첫 public subnet = 1a), ALB 1개, ECS Fargate 운영 서비스 task 0.5 vCPU·1GB(최소 1~2, 최대 4)와 staging 서비스 1 task([DEC-017](../DECISIONS.md#결정-목록)), ElastiCache Redis `cache.t4g.micro` 1노드(자동 장애 조치 없음), Aurora PostgreSQL Serverless v2 writer 1개(0~2 ACU, 5분 뒤 자동 일시정지), VPC endpoint 없음
- 업로드는 클라이언트 → ALB → ECS task → (NAT) → S3로 흐른다. 파일 최대 10MiB. GuardDuty Malware Protection이 S3 객체를 검사한다([DEC-018](../DECISIONS.md#결정-목록))
- 가격: us-east-1 온디맨드 정가, 산정일 2026-11-24, AWS Pricing API `GetProducts` 조회. 월 730시간. 세금·프리 티어·할인 미반영. 실제 청구는 Owner 비용 할당 태그 문제로 확인하지 못했다([포트폴리오 비용 한계](../PORTFOLIO.md#12-비용))

| 항목 | 단가 |
| --- | --- |
| NAT gateway | $0.045/시간 + $0.045/GB 처리 |
| 공인 IPv4 | $0.005/시간(개당, NAT EIP·ALB 노드마다) |
| ALB | $0.0225/시간 + $0.008/LCU-시간 |
| VPC endpoint | gateway: 무료. interface: AZ당 $0.01/시간 + $0.01/GB |
| EKS | 클러스터당 $0.10/시간 |
| Fargate | x86 $0.04048/vCPU-시간 + $0.004445/GB-시간, ARM $0.03238 + $0.00356 |
| Aurora Serverless v2 | $0.12/ACU-시간, 스토리지 $0.10/GB-월, I/O $0.20/100만 |
| RDS PostgreSQL | `db.t4g.micro` 단일 $0.016·Multi-AZ $0.032/시간, `db.t4g.small` 단일 $0.032·Multi-AZ $0.065/시간, gp3 단일 $0.115·Multi-AZ $0.23/GB-월 |
| ElastiCache | `cache.t4g.micro` Redis $0.016/시간(Valkey $0.0128) |
| S3 | PUT $0.005/1,000건 |
| 데이터 전송 | 인터넷 송신 $0.09/GB(첫 10TB). AZ 간 $0.01/GB 방향별(일반 공개 가격 기준, 이번 조회에서 확인하지 않음) |

계산에 쓴 단위 비용: Fargate x86 0.5 vCPU·1GB task 1개 = $0.0247/시간(월 약 $18.0), ARM은 $0.0198/시간(월 약 $14.4). NAT 1개 = 월 $32.9 + EIP $3.7. ALB = 월 $16.4 + 공인 IPv4 2개 $7.3 + LCU 1개 가정 $5.8. Aurora 0.5 ACU 상시 = 월 $43.8.

## 항목별 비교

### 1. ECS Fargate vs EKS

| 기준 | ECS Fargate (현재) | EKS (Fargate 또는 관리형 노드) |
| --- | --- | --- |
| 요구사항 적합성 | 단일 Spring Boot 서비스 + staging 1개. ALB target group·Auto Scaling·롤백(CICD-03)을 모두 ECS 기능으로 충족 | 같은 기능을 Deployment·HPA·Ingress(AWS Load Balancer Controller)로 구성. 서비스가 많거나 Kubernetes 생태계(Helm·Argo CD)가 요구될 때 이점 |
| 장애 영향 | 제어면은 AWS 관리. task 장애는 서비스 스케줄러가 교체(VERIFY-02 약 87초) | 제어면 관리형이지만 add-on(LB Controller·CoreDNS)·노드 그룹이 추가 장애 지점 |
| 월 비용 | 제어면 $0. task 비용만 | 클러스터 $73.0 추가. task 비용은 Fargate 동일 단가, 노드 방식이면 EC2 비용 |
| 운영 부담 | Terraform 모듈 1개, CD는 task definition 갱신 | 클러스터 버전 업그레이드 주기, add-on 버전 관리, IRSA/Pod Identity, 매니페스트·Helm 관리 |
| 결론 | **현재 유지** | **미채택**. 학습·직무 목적 검토는 K8S-01(P3, 선택·보류)에서 한다 |

### 2. Aurora Serverless v2 vs RDS PostgreSQL

| 기준 | Aurora Serverless v2 (현재, 0~2 ACU) | RDS `db.t4g.micro` 단일 AZ | RDS Multi-AZ (`db.t4g.small`) |
| --- | --- | --- | --- |
| 요구사항 적합성 | PostgreSQL 호환, PITR([DEC-020](../DECISIONS.md#결정-목록)), 쉬는 동안 0 ACU | PostgreSQL 호환, PITR | 같은 기능 + 동기 대기 인스턴스 |
| 장애 영향 | writer 1개라 인스턴스 장애 시 새 인스턴스 생성까지 중단. 0 ACU에서 깨어나는 동안 첫 연결 지연·기동 실패 위험(DEC-010) | 인스턴스·AZ 장애 시 복원까지 중단 | 대기 인스턴스로 장애 조치(보통 1~2분, AWS 문서 기준 추정) |
| 월 비용(20GB 가정) | 일시정지 시 스토리지만(몇 센트). 0.5 ACU 상시 $43.8, reader 추가 시 $87.6 | $11.7 + gp3 $2.3 = **$14.0** | $47.5 + gp3 $4.6 = **$52.1** (`t4g.micro` Multi-AZ는 $28.0) |
| 운영 부담 | ACU 범위·일시정지 시간만 관리. 깨우기 절차 필요(DEC-010) | 인스턴스 크기·스토리지 관리, 상시 과금이라 실습 계층처럼 지우거나 멈춰야 함(정지는 7일 뒤 자동 시작) | 단일 AZ와 같고 비용 두 배 |
| 결론 | **현재 유지**. 실습처럼 켜진 시간이 짧으면 가장 싸다 | 상시 운영·낮은 부하라면 Aurora 0.5 ACU 상시보다 싸다 → 상시 운영 전환 시 **개선 후보** | 가용성 강화 구성에서 Aurora reader와 비교할 **후보** |

판단 기준: 한 달에 Aurora가 깨어 있는 시간이 약 230시간(0.5 ACU 기준 $14.0 ÷ $0.06)을 넘으면 RDS `t4g.micro` 단일이 더 싸다(추정).

### 3. NAT 1개 vs AZ별 NAT vs Regional NAT gateway

| 기준 | NAT 1개 (현재, 1a) | AZ별 NAT 2개 | Regional NAT gateway |
| --- | --- | --- | --- |
| 요구사항 적합성 | private task의 ECR pull·CloudWatch Logs·SSM·S3 접근 충족 | 같음 | 같음. VPC 단위 하나로 AZ에 자동 확장·축소(AWS 문서) |
| 장애 영향 | 1a 장애 시 1b task도 외부 송신 불가 → 새 task 기동(ECR pull·비밀값 조회) 실패, S3 업로드·다운로드 실패. DB·Redis는 VPC 안이라 영향 없음 | AZ 하나가 죽어도 다른 AZ는 송신 유지 | AZ별 친화성 유지(AWS 문서). 새 AZ 확장에 최대 60분, 그동안 다른 AZ를 경유 |
| 월 비용 | $32.9 + EIP $3.7 = **$36.6** + 처리 $0.045/GB. 1b task 트래픽은 AZ 간 전송료 추가 | **$73.2** + 처리 동일. AZ 간 전송료 없음 | 가격표에 시간당 $0.045 항목 확인. AZ마다 과금되는지는 **미확인** → $36.6~$73.2 범위로 추정 |
| 운영 부담 | 라우트 1개 | public subnet·라우트 테이블을 AZ별로 나눠야 함(현재 private 라우트 테이블 1개 공유) | public subnet 없이 VPC 단위 라우트 1개. Terraform provider 지원 여부 **미확인** |
| 결론 | **현재 유지**(실습) | 가용성 강화 구성의 기본값 | 요금 방식·provider 지원 확인 뒤 AZ별 NAT 대신 쓸 **개선 후보** |

### 4. S3 경로: endpoint 없음 vs gateway vs interface

| 기준 | 없음 (현재, NAT 경유) | gateway endpoint | interface endpoint |
| --- | --- | --- | --- |
| 요구사항 적합성 | 동작함 | 같은 리전 S3 접근을 VPC 라우트로 처리. ECR 이미지 레이어도 S3에서 내려받으므로 같이 빠진다(AWS 문서 기준, 측정으로 확인 예정) | 같은 리전 S3 + 온프레미스·다른 리전에서 사설 접근 가능 |
| 장애 영향 | NAT 장애 = S3 장애 | NAT가 죽어도 S3 업로드·다운로드 유지. 단, 새 task 기동은 ECR API·Logs·SSM이 NAT를 쓰므로 여전히 실패 | gateway와 같음 |
| 월 비용 | S3 트래픽 GB당 NAT 처리 $0.045 | **$0** | AZ 2개 $14.6 + $0.01/GB |
| 운영 부담 | 없음 | 라우트 테이블 연결 1개. bucket policy에 `aws:SourceIp` 조건이 없어 영향 없음(관찰, Terraform 검색) | ENI·SG·DNS 관리 |
| 결론 | - | **개선 후보 → 이번에 측정** | 미채택(이 구성에서는 gateway로 충분) |

참고: NAT 없이 task를 띄우려면 ECR API·ECR DKR·Logs·SSM interface endpoint 4개가 필요하다. 4개 × AZ 2개 × $7.3 = 월 약 $58.4로 NAT 1개보다 비싸므로, 비용 목적이면 의미가 없다(추정).

### 5. 업로드 전송: ECS 중계 vs presigned URL

| 기준 | ECS 중계 (현재) | presigned URL (클라이언트 → S3 직접) |
| --- | --- | --- |
| 요구사항 적합성 | 확장자·크기(10MiB)를 서버에서 검증. `Idempotency-Key`(FILE-05, [ADR-001](../adr/001-UPLOAD-IDEMPOTENCY-KEY.md)), 객체 먼저 쓰고 DB 기록 후 실패 시 보상(FILE-01, [DEC-002](../DECISIONS.md#결정-목록))이 한 요청 안에서 끝남 | 업로드가 "URL 발급 → 클라이언트 PUT → 완료 통지" 세 단계가 된다. API 계약 변경 |
| 장애 영향 | 큰 업로드가 task 메모리·연결을 점유(FILE-04 스트리밍으로 완화) | 서버 부하·NAT·ALB 경로에서 업로드 바이트가 빠진다. 대신 완료 통지가 오지 않은 객체(고아)와 DB 상태 불일치가 새 장애 유형이 된다 |
| 월 비용 | 업로드 바이트가 ALB LCU·Fargate·NAT 처리($0.045/GB)를 거침. 10MiB 한도·시험 규모에서는 월 몇 센트 수준(추정) | NAT·ALB 처리 비용 없음. S3 요청 단가는 같음 |
| 운영 부담 | 현재 그대로 | 크기 제한을 서명 조건으로 강제(PUT은 `Content-Length` 서명, POST policy는 `content-length-range`), 멱등성·보상 로직 재설계, 검사 상태(FILE-03)를 S3 이벤트·완료 통지와 다시 맞춰야 함. CORS 설정 추가 |
| 결론 | **현재 유지** | **미채택**. 파일 한도가 커지거나(예: 100MB 이상) 업로드 트래픽이 비용·용량의 주된 요인이 되면 다시 검토한다 |

## 전체 구성 비교

가정: AZ 2개, 상시 운영은 730시간, DB 스토리지 20GB, ALB LCU 평균 1개, NAT 처리 데이터는 별도(GB당 $0.045). 실습 시간당 비용은 월 금액 ÷ 730이다(Aurora는 실습 중 0.5 ACU 활성 가정).

| 구성 요소 | 저비용 실습 구성 (현재) | 월 | 가용성 강화 구성 | 월 |
| --- | --- | --- | --- | --- |
| ECS | 운영 1 task + staging 1 task | $36.0 | 운영 2 task(AZ별 1개) + staging 1 task | $54.1 |
| NAT | 1개 + EIP | $36.6 | AZ별 2개 + EIP | $73.2 |
| ALB | 1개 + 공인 IPv4 2개 + LCU | $29.6 | 같음 | $29.6 |
| Redis | 1노드, 장애 조치 없음 | $11.7 | 2노드(복제본 + 자동 장애 조치) | $23.4 |
| DB | Aurora writer 0.5 ACU(실습 중 활성 가정) | $43.8 | Aurora writer + reader 각 최소 0.5 ACU (대안: RDS Multi-AZ `t4g.small` $52.1) | $87.6 |
| S3 경로 | NAT 경유 | $0 + GB당 $0.045 | gateway endpoint | $0 |
| **합계(상시)** | | **약 $158** | | **약 $268** (RDS Multi-AZ면 약 $232) |
| **실습 시간당** | | **약 $0.22** | | **약 $0.37** |
| 쉬는 동안 | 실습 계층 destroy, Aurora 0 ACU → 월 $1 미만 | | 상시 운영 전제라 해당 없음 | |

- 현재 구성은 상시 운영하면 월 약 $158로 예산 $10을 넘는다. 실습 때만 띄우고 destroy해서 지킨다(DEC-008). 2026-11-24 PERF-01 실습(약 3.5시간)은 정가 기준 약 $0.8로 추정한다.
- 가용성 강화 구성의 증가분(월 약 +$110)은 대부분 NAT(+$36.6)와 DB reader(+$43.8)다. 같은 목적에서 비용 대비 효과가 큰 순서는 S3 gateway endpoint($0) → Redis 장애 조치(+$11.7) → 운영 task 2개(+$18.0) → AZ별 NAT/Regional NAT → DB reader로 본다(추정, 장애 빈도 데이터 없음).

## 측정한 개선

세 가지 모두 Terraform 또는 코드 변경 전후를 같은 실습에서 같은 조건으로 잰다. 측정 지표가 서로 달라 한 실습에서 함께 바꿔도 결과가 섞이지 않는다.

### S3 gateway endpoint

- 가설: 고정 작업(일정 크기 업로드·다운로드 + task 이미지 pull)에서 NAT 처리 바이트가 S3·ECR 레이어 몫만큼 줄고, 업로드 지연은 같거나 줄어든다.
- 지표: NAT gateway CloudWatch `BytesOutToDestination`·`BytesInFromDestination` 합계, 작업별 응답 시간, 환산 비용($0.045/GB).
- 방법: endpoint 없이 고정 작업 실행 → endpoint 적용(실습 계층 변수) → 같은 작업 반복. 이미지 pull은 task를 새로 띄워 포함한다.
- 결과: 2026-11-24 실습 후 기록

### deregistration delay 300 → 30초

- 가설: task 교체·배포 시 이전 target 정리 대기가 줄어 교체 완료 시간과 2026-11-24 task 중지 시 중단 시간(6분 44초)이 줄어든다.
- 지표: task 중지부터 새 target `healthy`까지의 시간, 배포 완료 시간, 그 사이 ALB 5xx·`no-healthy-target` 알람.
- 방법: 같은 task 중지 훈련을 변경 전(300초)과 후(30초)에 한 번씩 실행한다.
- 결과: 2026-11-24 실습 후 기록

### 목록 API 커서 페이지 처리

- 가설: `GET /api/files`가 사용자 파일 전체 대신 최대 20개(기본)를 돌려주고 `(owner_id, id)` 인덱스를 써서, 시험 중 파일이 수천 건 쌓여도 p95가 일정하다(PERF-01에서는 ALB p95 0.39 → 1.96초로 늘었다).
- 지표: k6 `GET /api/files` p95·처리량, ALB p95, 오류율.
- 방법: PERF-01 스크립트(40 VU, 증가 2분·유지 6분·감소 1분)를 변경 전 이미지와 변경 후 이미지로 같은 Auto Scaling 조건에서 각각 실행한다.
- 결과: 2026-11-24 실습 후 기록

## 영향

- 이 문서는 비교와 후보 정리다. 채택하는 개선은 측정 뒤 [결정 목록](../DECISIONS.md#결정-목록)과 [AWS 구성](../design/AWS.md)에 반영하고, 목록 API 계약 변경은 [API](../design/API.md)에 반영한다.
- 미채택 대안(EKS, presigned URL, interface endpoint)과 가용성 강화 구성은 여기에 보존한다.

## 미정

- Regional NAT gateway의 AZ별 과금 방식과 Terraform AWS provider 지원
- 실제 청구 금액(Owner 비용 할당 태그 활성 여부, 조직 관리자 확인 대기)
- AZ 장애·NAT 장애는 실습에서 재현하지 않았다. 장애 영향은 구성과 AWS 문서에 근거한 추정이다

## 다음 확인

- 2026-11-24 실습에서 세 개선의 전후 결과를 위 "결과" 줄에 채우고 채택 여부를 결정 목록에 남긴다.
- 상시 운영으로 바꾸거나 가용성 목표가 생기면 전체 구성 비교의 순서대로 다시 검토한다.
