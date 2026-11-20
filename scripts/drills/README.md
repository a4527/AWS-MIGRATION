AWS 실습 계층에서 쓰는 3단계 검증 스크립트다. 실행 결과는 [작업일지](../../docs/worklogs/2026-11-20.md#3단계-실행), 시나리오 정의는 [VALIDATION 12장](../../docs/design/VALIDATION.md#12-운영-보강-검증-3단계)에 있다. 모두 `AWS_PROFILE=bootcamp-tf`와 `terraform/environments/dev`의 output을 쓰고, 첫 인자로 타임라인을 남길 파일 경로를 받는다.

| 스크립트 | 시나리오 | 실행 |
| --- | --- | --- |
| `ops01_failure_drills.py` | V-OPS-01~05 | `python3 -I ops01_failure_drills.py <out> pull,db,s3,redis,outage` |
| `ops02_restore_drill.py` | V-BAK-01 | `python3 -I ops02_restore_drill.py <out> <owner-tag> [--keep] [--resume]` |
| `file03_scan_checks.py` | V-SCAN-01~05 | `python3 -I file03_scan_checks.py <out>` |
| `perf01_load.py` + `perf01_load.js` | V-PERF-03 | `python3 -I perf01_load.py <out> baseline|scaling <owner-tag>` |

주의: 모두 실제 리소스를 바꾼다(배포·IAM 정책 분리·Redis 재부팅·desired 0·복원 클러스터 생성). 각 스크립트가 끝에 되돌리지만, 중간에 멈추면 직접 원복한다. `perf01_load.py`는 scalable target max를 바꾸므로 끝나면 `terraform apply`로 원래 값(1~4)을 맞춘다.
