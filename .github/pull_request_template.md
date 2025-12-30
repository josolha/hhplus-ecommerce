## :pushpin: PR 제목 규칙
[STEP19-20] 조솔하 - e-commerce

---

## 📋 작업 내용

### STEP 19 부하 테스트 스크립트 작성 및 진행
- [x] 부하 테스트 대상 선정 및 목적, 시나리오 등의 계획을 세우고 이를 문서로 작성
  - 3개 핵심 API 선정: 쿠폰 발급, 잔액 충전, 주문/결제
  - k6 도구 선정 및 Load/Peak Test 시나리오 설계
  - 100,000명 테스트 데이터 생성 (LoadTestDataSeeder)

- [x] 적합한 테스트 스크립트를 작성하고 수행
  - `k6-tests/coupon-issue-test.js`: 쿠폰 발급 (VU 50→200, TPS 372 달성)
  - `k6-tests/balance-charge-test.js`: 잔액 충전 (VU 150, TPS 551 달성)
  - `k6-tests/order-payment-test.js`: 주문/결제 (VU 100, TPS 81 달성)
  - 상세 결과: `docs/loadtest/LOAD_TEST_TOTAL.md`


### STEP 20 부하 테스트로 인한 문제 개선 및 보고서 작성
- [x] 테스트를 진행하며 획득한 다양한 성능 지표를 분석 및 시스템 내의 병목을 탐색 및 개선함
  - **문제 1**: 전역 분산 락으로 인한 TPS 7.54 병목 → 사용자별 락으로 변경 (TPS 10.8배 향상)
  - **문제 2**: cart_items 테이블 Full Table Scan → 인덱스 추가 (쿼리 30배 개선)
  - **문제 3**: Connection Pool 부족 (10개) → 100개로 확대 (응답시간 67% 개선)
  - **문제 4**: k6 응답 검증 필드명 불일치 → 수정 (에러율 26.78% → 0%)

- [x] 가상의 장애 대응 문서를 작성하고 제출함
  - 주문 API 전역 락 장애 시나리오 작성 (35분 장애 가정)
  - 5-whys 근본 원인 분석 수행
  - Short/Mid/Long-term 액션 아이템 정리
  - 문서: `docs/loadtest/INCIDENT_REPORT.md`

---

## 🔍 주요 개선 사항

### 1. Lock Granularity 최적화
```java
// Before: 전역 락 (모든 사용자가 대기)
@DistributedLock(key = "'order:global'")

// After: 사용자별 락 (동시 처리 100배 향상)
@DistributedLock(key = "'order:user:' + #request.userId")
```
**효과**: TPS 7.54 → 81.68 (10.8배 향상)

### 2. DB 인덱스 추가
```java
@Table(name = "cart_items", indexes = {
    @Index(name = "idx_cart_id", columnList = "cart_id")
})
```
**효과**: 장바구니 조회 60ms → 2ms (30배 개선)

### 3. Connection Pool 확대
```yaml
maximum-pool-size: 10 → 100
minimum-idle: 20
```
**효과**: p(95) 응답시간 404ms → 131ms (67% 개선)

---

## 📊 테스트 결과 요약

| API | TPS | p(95) 응답시간 | 에러율 | 동시성 제어 |
|-----|-----|---------------|--------|------------|
| 쿠폰 발급 | 372.32 | 96.69ms | 25.93% (정상) | 중복 발급 0건 ✅ |
| 잔액 충전 | 551.59 | 287.65ms | 0.00% | 동시성 충돌 0건 ✅ |
| 주문/결제 | 81.68 | 131ms | 8.62% | 재고 정합성 보장 ✅ |

**모든 API 목표 달성** ✅

---

## 📁 작성 문서

- `docs/loadtest/LOAD_TEST_TOTAL.md`: 부하 테스트 종합 보고서 (78KB)
  - 테스트 대상 선정 이유
  - API별 상세 테스트 결과
  - 성능 최적화 과정
  - 개선 전후 비교

- `docs/loadtest/INCIDENT_REPORT.md`: 장애 대응 보고서 (35KB)
  - 가상 장애 시나리오 (35분 장애)
  - 타임라인 및 영향 범위
  - 5-whys 근본 원인 분석
  - Short/Mid/Long-term 액션 아이템

---

## 🧪 테스트 환경

- **부하 테스트 도구**: k6 v0.54.0
- **테스트 데이터**: 100,000명 유저 (LoadTestDataSeeder)
- **시스템**: Spring Boot 3.5.7, MySQL 8.0, Redis 7.2
- **환경**: macOS, 8 Core CPU, 16GB RAM

---

## 💡 간단 회고 (3줄 이내)

- **잘한 점**: 부하 테스트로 프로덕션 전 3가지 치명적 병목 발견 및 해결. Lock Granularity 최적화로 TPS 10.8배 향상 달성. 5-whys 분석으로 근본 원인(배포 프로세스) 도출.

- **어려운 점**: k6 메트릭 구조 파악 및 응답 필드 검증 로직 작성에 시행착오. 전역 락의 문제점을 부하 테스트 전에는 발견하지 못함. 100,000명 데이터 생성 시 메모리 관리.

- **다음 시도**: 배포 전 부하 테스트 필수화 (CI/CD 통합). APM 도구 도입으로 실시간 병목 탐지. Spike/Soak Test 추가로 시스템 한계 측정.