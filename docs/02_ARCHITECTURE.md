# 02. Architecture

## 온프레미스 아키텍처

```text
Client
  -> Nginx Reverse Proxy
  -> Spring Boot API
  -> PostgreSQL
  -> Redis
  -> MinIO
```

## 데이터 흐름

### 인증 흐름

1. Client가 로그인 요청을 보낸다.
2. Nginx가 요청을 Spring Boot API로 전달한다.
3. Spring Boot가 PostgreSQL에서 사용자 정보를 조회한다.
4. 인증 성공 시 JWT를 발급한다.
5. Client는 이후 요청에 JWT를 포함한다.

### 파일 업로드 흐름

1. Client가 `multipart/form-data` 요청으로 파일을 전송한다.
2. Nginx가 업로드 요청을 Spring Boot API로 전달한다.
3. Spring Boot가 파일 크기, 확장자, 권한을 검증한다.
4. 파일 바이너리는 MinIO에 저장한다.
5. 메타데이터는 PostgreSQL에 저장한다.
6. 빈번한 조회를 위해 필요한 경우 Redis에 캐시를 반영한다.

### 파일 다운로드 흐름

1. Client가 파일 다운로드를 요청한다.
2. Spring Boot가 권한과 파일 상태를 확인한다.
3. Spring Boot가 MinIO에서 바이너리를 조회한다.
4. 파일 스트림을 Client로 반환한다.

### 파일 삭제 흐름

1. Client가 삭제 요청을 보낸다.
2. Spring Boot가 권한을 확인한다.
3. 파일 상태를 `deleted`로 변경한다.
4. 메타데이터는 즉시 논리 삭제로 반영한다.
5. 실제 바이너리 정리는 후속 운영 절차로 분리할 수 있다.

### 서버리스 후처리 흐름

1. 파일이 S3에 저장되면 ObjectCreated 이벤트가 발생한다.
2. S3 이벤트가 Lambda를 호출한다.
3. Lambda가 바이러스 검사와 메타데이터 추출을 수행한다.
4. Lambda가 검사 결과와 추출 메타데이터를 PostgreSQL 또는 Aurora에 반영한다.
5. 필요 시 CloudWatch에 로그와 지표를 남긴다.
6. 서버리스 작업 결과는 운영 이력과 추적 가능해야 한다.

### 구성 역할

- Nginx: 외부 진입점, TLS 종료 지점, reverse proxy, 요청 라우팅
- Spring Boot: 인증, 인가, 파일 메타데이터 처리, 권한 검증, 업로드/다운로드 제어
- PostgreSQL: 사용자 계정, 파일 메타데이터, 권한 정보, 운영 로그 저장
- Redis: 자주 조회되는 메타데이터 캐시, 단기 조회 성능 개선
- MinIO: 실제 파일 바이너리 저장소

## 책임 분리

- 요청 라우팅과 연결 관리는 Nginx가 담당한다.
- 비즈니스 규칙과 권한 판단은 Spring Boot가 담당한다.
- 영속 데이터는 PostgreSQL이 담당한다.
- 캐시성 데이터는 Redis가 담당한다.
- 파일 본문은 MinIO가 담당한다.

## AWS Architecture

```text
Route53
  -> ALB
  -> Amazon EKS
  -> Aurora PostgreSQL
  -> ElastiCache
  -> Amazon S3
  -> Lambda
  -> CloudWatch
```

### 구성 역할

- Route53: 도메인 라우팅
- ALB: HTTPS 진입점 및 트래픽 분산
- EKS: 애플리케이션 실행 환경
- Aurora: 관리형 관계형 DB
- ElastiCache: 관리형 캐시
- S3: 파일 저장소 및 이벤트 발생 지점
- Lambda: S3 이벤트 기반 바이러스 검사, 메타데이터 추출, 비동기 보조 작업
- CloudWatch: 로그, 메트릭, 알람

## 서버리스 책임

- S3는 업로드 완료 이벤트의 발생 지점이 된다.
- Lambda는 애플리케이션 본체에서 분리할 수 있는 바이러스 검사와 메타데이터 추출을 담당한다.
- Lambda는 검사 결과와 추출 메타데이터를 저장 계층에 반영한다.
- CloudWatch는 Lambda 실행 결과와 오류를 추적한다.
- 서버리스 작업은 파일 저장과 메타데이터 저장의 핵심 경로를 대체하지 않는다.

## 온프레미스와 AWS 비교 기준

- 애플리케이션 기능은 동일하게 유지한다.
- 저장소는 MinIO에서 S3로 교체한다.
- DB는 PostgreSQL에서 Aurora PostgreSQL로 교체한다.
- 캐시는 Redis에서 ElastiCache로 교체한다.
- 실행 환경은 Docker Compose에서 EKS로 교체한다.

## 마이그레이션 원칙

- 기능은 동일하게 유지하고 인프라만 교체한다.
- 파일 저장 방식은 MinIO에서 S3로 교체한다.
- DB는 PostgreSQL 호환성을 유지한 채 Aurora로 이동한다.
- 캐시는 Redis에서 ElastiCache로 전환한다.
- 배포 방식은 Docker Compose에서 Kubernetes로 변경한다.

## Day 1 기준

- 온프레미스 요청 흐름이 문서화된다.
- 각 구성요소의 책임이 분리된다.
- AWS 섹션은 비교 기준만 제공하고 온프레미스 설명과 섞지 않는다.
