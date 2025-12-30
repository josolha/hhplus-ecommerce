# E-commerce Core System

E-commerce 백엔드 시스템 (Spring Boot 3.5.7, MySQL 8.0, Redis 7.2)

---

## 부하 테스트 및 성능 최적화

### 테스트 도구: k6 선택 이유
- Go 기반 경량 고성능 (단일 머신에서 수만 VU 생성)
- JavaScript 스크립트로 학습 곡선 낮음
- ramping-vus로 실제 트래픽 패턴 재현 가능

### 테스트 대상
- 쿠폰 발급 API (선착순 이벤트 대비)
- 잔액 충전 API (동시성 검증)
- 주문/결제 API (복합 트랜잭션 성능)

### 테스트 결과

| API | TPS | p(95) 응답시간 | 동시성 제어 |
|-----|-----|---------------|------------|
| 쿠폰 발급 | 372 req/s | 97ms | 중복 발급 0건 ✅ |
| 잔액 충전 | 551 req/s | 288ms | 충돌 0건 ✅ |
| 주문/결제 | 82 req/s | 131ms | 재고 정합성 보장 ✅ |

### 발견한 문제 및 해결

**문제 1: 주문 API 전역 락 병목**
- 증상: TPS 7.54 (목표 50 미달)
- 원인: 모든 사용자가 `LOCK:order:global` 하나 공유
- 해결: 사용자별 락으로 변경 (`LOCK:order:user:{userId}`)
- 결과: **TPS 10.8배 향상** (7.54 → 81.68)

**문제 2: 장바구니 Full Table Scan**
- 증상: 조회 60ms 소요 (전체 처리 시간의 88%)
- 원인: `cart_items` 테이블 인덱스 누락
- 해결: `cart_id` 인덱스 추가
- 결과: **30배 개선** (60ms → 2ms)

**문제 3: Connection Pool 부족**
- 증상: VU 100 환경에서 90개 요청 대기
- 원인: HikariCP maximum-pool-size 10 (기본값)
- 해결: 100으로 확대
- 결과: **p(95) 67% 개선** (404ms → 131ms)

---

## 문서

- **[부하 테스트 종합 보고서](docs/loadtest/LOAD_TEST_TOTAL.md)** - 테스트 계획, 실행, 결과 분석
- **[장애 대응 보고서](docs/loadtest/INCIDENT_REPORT.md)** - 가상 장애 시나리오 및 5-whys 분석

---

## 실행 방법

```bash
# 부하 테스트 데이터 생성 (100,000명)
./gradlew test --tests "LoadTestDataSeeder.seedForLoadTest"

# k6 부하 테스트 실행
k6 run k6-tests/coupon-issue-test.js
k6 run k6-tests/balance-charge-test.js
k6 run k6-tests/order-payment-test.js
```
