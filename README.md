# 부하테스트 및 성능 최적화

대규모 트래픽 환경에서 시스템 안정성과 동시성 제어를 검증하기 위한 k6 부하 테스트를 수행했습니다.

---

## 전체 테스트 구조

```
┌─────────────────────────────────────────────────────────┐
│                   k6 부하 테스트                         │
│                                                          │
│   쿠폰 발급 테스트          주문/결제 테스트              │
│   - 최대 500 VUs           - 최대 100 VUs               │
│   - 150K 사용자            - 150K 사용자                │
│   - 100K 쿠폰 재고         - 10K 상품 재고              │
└─────────────────────────────────────────────────────────┘
       │                              │
       ▼                              ▼
┌─────────────────┐          ┌─────────────────┐
│  쿠폰 발급 API   │          │   주문/결제 API  │
│                 │          │                 │
│ Redis Set       │          │ Redisson Lock   │
│ Redis Counter   │          │ (사용자별 락)    │
│ Kafka Queue     │          │                 │
└─────────────────┘          └─────────────────┘
       │                              │
       ▼                              ▼
┌─────────────────┐          ┌─────────────────┐
│  MySQL DB       │          │  MySQL DB       │
│  - 쿠폰 재고     │          │  - 상품 재고     │
│  - 발급 이력     │          │  - 주문 생성     │
│                 │          │  - 잔액 차감     │
└─────────────────┘          └─────────────────┘
```

---

## 1️⃣ 쿠폰 발급 시스템

### 문제점
- **선착순 경쟁**: 한정된 쿠폰을 대규모 동시 요청으로 발급
- **중복 발급 방지**: 같은 사용자가 여러 번 요청 시 중복 차단 필요
- **재고 정합성**: Redis-DB 간 재고 불일치 가능성

### 해결방법

#### 3단계 동시성 제어 전략
```
├─ 1단계: Redis Set (중복 발급 방지, O(1) 속도)
│  SADD coupon:issued:{couponId} {userId}
│  → 0이면 중복, 1이면 통과
│
├─ 2단계: Redis Counter (재고 빠른 확인, 원자적 DECR)
│  DECR coupon:stock:{couponId}
│  → 음수면 품절, 0 이상이면 통과
│
└─ 3단계: DB UPDATE (최종 정합성 보장)
   UPDATE coupons SET issued = issued + 1
   WHERE id = ? AND available > 0
   → affected rows = 0이면 품절
```

#### 비동기 처리 흐름
```
API 요청 → Redis 검증 → 202 Accepted (즉시 응답)
                ↓
        Kafka Queue → Consumer → DB 처리
```

#### 보상 트랜잭션 (롤백 메커니즘)
Redis와 DB 간 불일치 발생 시 자동으로 보상 처리:

```java
try {
    // DB 쿠폰 발급 시도
    couponIssueService.issue(userId, couponId);
} catch (CouponSoldOutException e) {
    // 1. Redis 재고 복구
    redisService.incrementStock(couponId);

    // 2. Redis Set 제거 (재시도 가능)
    redisService.removeFromIssuedSet(couponId, userId);

    // 3. 품절 플래그 설정 (빠른 차단)
    redisService.setSoldOutFlag(couponId);
} catch (DuplicateCouponIssueException e) {
    // DB에서 중복 발견 시
    redisService.incrementStock(couponId);
    redisService.removeFromIssuedSet(couponId, userId);
}
```

**보상 시나리오:**
| 상황 | Redis 동작 | DB 동작 | 보상 처리 |
|------|-----------|---------|----------|
| 정상 발급 | DECR 성공 | UPDATE 성공 | - |
| DB 품절 | DECR 성공 | UPDATE 실패 | INCR + SREM + 플래그 설정 |
| DB 중복 | DECR 성공 | EXISTS = true | INCR + SREM |
| Redis 품절 | DECR → -1 | 미실행 | INCR (즉시) |

### 주요 성과
| 메트릭 | 결과 | 설명 |
|--------|------|------|
| **TPS** | 372 req/s | 초당 372건 처리 |
| **응답시간 (p95)** | 97ms | 95% 요청이 97ms 이내 |
| **중복 발급** | 0건 | Redis Set으로 완벽 차단 |
| **재고 정합성** | 100% | 보상 트랜잭션으로 Redis-DB 일치 |
| **시스템 에러** | 0% | 안정적인 처리 |

---

## 2️⃣ 주문/결제 시스템

### 문제점
- **동시 주문**: 같은 사용자의 중복 주문 방지
- **재고 경합**: 여러 사용자가 같은 상품 동시 구매
- **잔액 정합성**: 결제 처리 중 잔액 일관성 보장
- **트랜잭션 범위**: 재고 차감 + 결제 + 이벤트 저장 원자성

### 해결방법

#### 사용자별 분산 락 전략
```
Redisson 분산 락 (사용자별 독립적 처리)
├─ 락 키: lock:order:user:{userId}
├─ 다른 사용자는 병렬 처리 → 높은 TPS
└─ 같은 사용자는 순차 처리 → 중복 방지

DB 레벨 안전장치
├─ 재고 차감: UPDATE ... WHERE quantity >= :amount
├─ 잔액 차감: Balance VO로 불변성 보장
└─ 이력 저장: transaction_id UNIQUE 제약
```

#### 트랜잭션 처리 흐름
```
분산 락 획득 (사용자별)
    ↓
트랜잭션 시작
    ↓
재고 차감 (원자적 UPDATE)
UPDATE products SET quantity = quantity - ?
WHERE id = ? AND quantity >= ?
    ↓
주문 생성 (Order + OrderItems)
    ↓
결제 처리
├─ 잔액 확인 및 차감
├─ BalanceHistory INSERT (transaction_id)
└─ Payment 상태 업데이트
    ↓
쿠폰 사용 (UserCoupon.use())
    ↓
Outbox 이벤트 저장 (같은 트랜잭션)
    ↓
트랜잭션 커밋
    ↓
분산 락 해제
```

### 주요 성과
| 메트릭 | 결과 | 설명 |
|--------|------|------|
| **TPS** | 82 req/s | 복잡한 트랜잭션 처리 |
| **응답시간 (p95)** | 131ms | 분산 락 포함 |
| **재고 정합성** | 100% | 음수 재고 0건 |
| **잔액 정합성** | 100% | transaction_id 기반 멱등성 |
| **시스템 에러** | 0% | 안정적인 트랜잭션 |

---

## 핵심 최적화 기법

### 1. 전역 락 → 사용자별 락 전환
```java
// AS-IS: 모든 주문이 순차 처리 (TPS 7.54)
@DistributedLock(key = "'order:global'")

// TO-BE: 사용자별 독립 처리 (TPS 81.68, 10.8배 향상)
@DistributedLock(key = "'order:user:' + #userId")
```

**효과:**
- 다른 사용자는 병렬 처리로 TPS 대폭 증가
- 같은 사용자는 순차 처리로 중복 주문 차단
- 데드락 없음 (각 사용자는 단일 락만 획득)

### 2. 장바구니 인덱스 추가
```sql
-- AS-IS: Full Table Scan (60ms)
SELECT * FROM cart_items WHERE cart_id = ?;

-- TO-BE: Index Scan (2ms, 30배 개선)
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
```

**효과:**
- 장바구니 조회 시간 60ms → 2ms
- 주문 생성 초기 단계 병목 해소
- 전체 주문 응답시간 개선에 기여

### 3. Redis 재고 관리 + DB 최종 검증
```
빠른 응답        최종 정합성
   ↓                ↓
Redis DECR  →  DB UPDATE WHERE available > 0
(밀리초 단위)    (원자적 보장)
     ↓
실패 시 보상 트랜잭션 (INCR)
```

**효과:**
- 빠른 응답: Redis 검증으로 밀리초 단위 응답
- 정합성 보장: DB UPDATE 조건으로 최종 검증
- 불일치 해소: 보상 트랜잭션으로 Redis-DB 동기화

### 4. HikariCP Connection Pool 확대
```yaml
# AS-IS
spring.datasource.hikari.maximum-pool-size: 10

# TO-BE
spring.datasource.hikari.maximum-pool-size: 100
```

**효과:**
- 동시 처리 가능한 트랜잭션 수 증가
- p(95) 응답시간 404ms → 131ms (67% 개선)
- Connection 대기 시간 감소

---

## 📊 상세 보고서

부하 테스트의 전체 설계, 시퀀스 다이어그램, 성능 메트릭, 기술적 의사결정 과정은 상세 보고서에서 확인할 수 있습니다.

### [쿠폰 발급 시스템 부하 테스트 보고서](docs/COUPON_LOAD_TEST_REPORT.md)
- **시스템 아키텍처**: Redis Set + Kafka 기반 선착순 발급 시스템
- **동시성 제어**: 3단계 동시성 제어 전략 상세 설명
- **보상 트랜잭션**: Redis-DB 불일치 해결 메커니즘
- **시퀀스 다이어그램**: 전체 프로세스 플로우 및 상태 전이도
- **테스트 시나리오**: Sequential/Load/Peak 테스트 설계
- **성능 메트릭**: 커스텀 메트릭 및 Threshold 설정
- **병목 분석**: Redis 재고 확인, Kafka Consumer, DB UPDATE 성능

### [주문/결제 시스템 부하 테스트 보고서](docs/ORDER_PAYMENT_LOAD_TEST_REPORT.md)
- **시스템 아키텍처**: Redisson 분산 락 기반 주문 처리 시스템
- **동시성 제어**: 사용자별 분산 락 + DB 레벨 재고 검증
- **트랜잭션 관리**: 재고 차감, 결제, Outbox 원자성 보장
- **시퀀스 다이어그램**: 주문/결제 전체 플로우 및 예외 처리
- **테스트 시나리오**: Ramping VUs를 활용한 점진적 부하 증가
- **성능 메트릭**: 비즈니스 실패 vs 시스템 실패 분류
- **병목 분석**: 분산 락 대기, DB UPDATE, Payment 트랜잭션 성능
- **기술적 의사결정**: Redisson vs Lettuce, 사용자별 락 선택 이유

---
