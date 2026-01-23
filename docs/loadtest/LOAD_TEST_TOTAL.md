# E-commerce 핵심 API 부하 테스트 종합 보고서

**작성일**: 2025-12-26
**프로젝트**: E-commerce Core System
**테스트 도구**: k6 (Grafana Labs)
**테스트 환경**: Spring Boot 3.5.7, MySQL 8.0, Redis 7.2

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [테스트 대상 API 선정 이유](#2-테스트-대상-api-선정-이유)
3. [테스트 환경 및 도구](#3-테스트-환경-및-도구)
4. [API별 테스트 결과](#4-api별-테스트-결과)
5. [종합 분석](#5-종합-분석)
6. [결론 및 향후 계획](#6-결론-및-향후-계획)

---

## 1. 프로젝트 개요

### 1.1 배경

E-commerce 시스템의 안정적인 서비스 제공을 위해서는 실제 트래픽 환경에서의 성능 검증이 필수적입니다. 본 프로젝트는 시스템의 핵심 API 3개에 대한 부하 테스트를 설계하고 수행하여, 실제 운영 환경에서 발생할 수 있는 성능 문제를 사전에 발견하고 개선하는 것을 목표로 합니다.

### 1.2 테스트 목적

1. **동시성 제어 검증**: 대규모 동시 요청 상황에서 데이터 무결성 보장 확인
2. **성능 기준선 수립**: 각 API별 TPS, 응답 시간 등 성능 지표의 기준값 확립
3. **병목 지점 식별**: 실제 부하 환경에서의 성능 저하 원인 분석
4. **시스템 한계 측정**: 현재 시스템이 처리 가능한 최대 부하 수준 파악

### 1.3 테스트 범위

본 보고서는 다음 3개 핵심 API에 대한 부하 테스트 결과를 다룹니다:

1. **쿠폰 발급 API** (POST `/api/coupons/{couponId}/issue`)
2. **잔액 충전 API** (POST `/api/users/{userId}/balance/charge`)
3. **주문/결제 API** (POST `/api/orders`)

---

## 2. 테스트 대상 API 선정 이유

### 2.1 선정 기준

E-commerce 시스템의 여러 API 중 다음 기준에 따라 테스트 대상을 선정했습니다:

1. **비즈니스 임팩트**: 매출 및 사용자 경험에 직접적인 영향을 미치는 API
2. **동시성 복잡도**: 높은 동시 접속 상황에서 데이터 무결성 보장이 중요한 API
3. **트랜잭션 복잡도**: 여러 엔티티를 수정하는 복잡한 트랜잭션을 포함하는 API
4. **부하 발생 가능성**: 특정 시점(이벤트, 피크 타임)에 집중적인 트래픽이 예상되는 API

### 2.2 쿠폰 발급 API 선정 이유

**비즈니스 특성:**
- 선착순 이벤트 시 수만 명이 동시에 요청
- 타임딜, 플래시 세일 등 마케팅 핵심 기능
- 쿠폰 소진 시점 전후로 극단적인 트래픽 변화

**기술적 과제:**
- **중복 발급 방지**: 동일 사용자가 여러 번 요청해도 1개만 발급되어야 함
- **재고 관리**: 100,000개 한정 쿠폰에 대해 정확히 100,000명에게만 발급
- **동시성 제어**: Redis 분산 락을 통한 경쟁 조건 해결
- **응답 성능**: 초당 수백 건의 요청을 안정적으로 처리

**검증 목표:**
- 중복 발급 0건 달성
- TPS 300 이상 유지
- p(95) 응답 시간 1000ms 이내

### 2.3 잔액 충전 API 선정 이유

**비즈니스 특성:**
- 결제 수단으로 사용되는 잔액의 충전 기능
- 동일 사용자가 짧은 시간 내 여러 번 충전 시도 가능
- 금융 데이터의 정확성이 매우 중요

**기술적 과제:**
- **동시 충전 방어**: 동일 사용자의 동시 요청 시 잔액 정합성 보장
- **Pessimistic Lock**: JPA `@Lock(PESSIMISTIC_WRITE)`를 통한 트랜잭션 격리
- **Lock 대기 시간**: 높은 동시성 환경에서 Lock으로 인한 응답 지연 최소화
- **Connection Pool**: 동시 접속자 수에 맞는 DB Connection 설정

**검증 목표:**
- 동시성 충돌 0건 달성
- TPS 300 이상 유지
- p(95) 응답 시간 1500ms 이내

### 2.4 주문/결제 API 선정 이유

**비즈니스 특성:**
- E-commerce의 최종 목표이자 핵심 기능
- 장바구니, 재고, 잔액, 쿠폰 등 모든 도메인이 연관된 복합 트랜잭션
- 점심/저녁 피크 타임에 주문이 집중됨

**기술적 과제:**
- **복합 트랜잭션**: 재고 차감, 잔액 차감, 쿠폰 사용, 주문 생성의 원자성 보장
- **다중 Lock**: 재고 Lock, 잔액 Lock, 주문 생성 Lock의 조율
- **쿼리 성능**: 장바구니 조회, 재고 조회 등 다수의 읽기 작업 최적화
- **분산 락 전략**: 전역 Lock vs 사용자별 Lock의 성능 트레이드오프

**검증 목표:**
- TPS 50 이상 달성
- p(95) 응답 시간 2000ms 이내
- 재고 부족, 잔액 부족 등 비즈니스 에러율 15% 이내

### 2.5 테스트 우선순위

선정한 3개 API의 테스트 우선순위는 다음과 같습니다:

| 순위 | API | 우선순위 설정 이유 |
|------|-----|------------------|
| 1 | 주문/결제 | 가장 복잡한 트랜잭션, 모든 도메인 연관, 최종 매출 직결 |
| 2 | 쿠폰 발급 | 이벤트 시 극단적 트래픽, 중복 발급 방지 중요 |
| 3 | 잔액 충전 | 금융 데이터 정확성, Pessimistic Lock 검증 |

---

## 3. 테스트 환경 및 도구

### 3.1 부하 테스트 도구: k6

#### 선정 이유

**1) 경량성과 고성능**
- Go 언어로 작성되어 단일 머신에서 수만 VU 생성 가능
- JavaScript 기반 스크립트로 학습 곡선이 낮음
- HTTP/2, WebSocket 등 최신 프로토콜 지원

**2) 시나리오 기반 테스트**
- ramping-vus executor로 실제 트래픽 패턴 재현
- 여러 시나리오를 동시에 실행 가능
- Warm-up, Peak, Cool-down 단계 구성 가능

**3) 메트릭 및 임계값**
- Counter, Trend, Rate, Gauge 등 다양한 메트릭 타입
- 커스텀 메트릭으로 비즈니스 로직별 추적
- Threshold 설정으로 테스트 자동 성공/실패 판정

**4) CI/CD 통합**
- CLI 기반으로 자동화 파이프라인 구축 용이
- JSON, CSV 등 다양한 형식으로 결과 출력
- Grafana Cloud와의 네이티브 연동

### 3.2 테스트 데이터 준비

#### LoadTestDataSeeder

부하 테스트를 위한 대용량 테스트 데이터를 생성하는 전용 시더를 구현했습니다:

**생성 데이터:**
- 사용자: 100,000명 (test-user-1 ~ test-user-100000)
- 각 사용자 초기 잔액: 1,000,000원
- 장바구니: 100,000개 (각 사용자당 1개, 상품 3개씩 담김)
- 쿠폰: 100,000개 한정 (test-coupon-1)
- 상품: 10개 (충분한 재고)

**배치 처리:**
- JdbcTemplate의 batchUpdate 사용
- 1,000건씩 배치로 삽입하여 메모리 효율성 확보
- 총 79초만에 100,000명 유저 데이터 생성

**데이터 설계 근거:**
- 충분한 유저 풀로 Lock 충돌 최소화
- 랜덤 유저 선택으로 실제 트래픽 패턴 재현
- 재고 충분히 확보하여 순수 동시성 성능 측정

### 3.3 테스트 환경

**하드웨어:**
- CPU: 8 Core
- RAM: 16GB
- Disk: SSD

**소프트웨어:**
- OS: macOS (Darwin 24.5.0)
- Java: OpenJDK 17
- Spring Boot: 3.5.7
- MySQL: 8.0
- Redis: 7.2
- k6: v0.54.0

**네트워크:**
- 로컬 환경 (localhost:8081)
- 네트워크 지연 없음
- 순수 애플리케이션 성능 측정

---

## 4. API별 테스트 결과

### 4.1 쿠폰 발급 API

#### 테스트 시나리오

**부하 패턴:**
```javascript
scenarios: {
  load_test: {
    executor: 'ramping-vus',
    stages: [
      { duration: '30s', target: 50 },
      { duration: '1m', target: 50 },
      { duration: '30s', target: 0 },
    ],
  },
  peak_test: {
    executor: 'ramping-vus',
    stages: [
      { duration: '10s', target: 200 },
      { duration: '30s', target: 200 },
      { duration: '10s', target: 0 },
    ],
    startTime: '2m',
  },
}
```

**테스트 전략:**
- Load Test: 50 VU로 일반적인 부하 시뮬레이션
- Peak Test: 200 VU로 이벤트 시작 시점의 극단적 트래픽 재현
- 총 2분 50초 동안 진행

#### 테스트 결과

**핵심 지표:**

| 메트릭 | 결과 | 목표 | 상태 |
|--------|------|------|------|
| 총 요청 수 | 63,331건 | - | - |
| 평균 TPS | 372.32 req/s | 200+ | 달성 |
| 평균 응답 시간 | 29.84ms | <50ms | 달성 |
| p(50) 응답 시간 | 20.30ms | - | 우수 |
| p(95) 응답 시간 | 96.69ms | <1000ms | 달성 |
| p(99) 응답 시간 | 178.63ms | <2000ms | 달성 |
| 에러율 | 25.93% | - | 정상 |
| 쿠폰 발급 성공 | 46,911개 | - | - |
| 중복 발급 | 0건 | 0 | 완벽 |

**데이터베이스 검증:**
```sql
-- 쿠폰 상태
SELECT id, total_quantity, issued_quantity, remaining_quantity
FROM coupons WHERE id = 'test-coupon-1';

결과:
total_quantity: 100,000
issued_quantity: 46,911
remaining_quantity: 53,089

-- 중복 발급 검증
SELECT COUNT(*) as total_records, COUNT(DISTINCT user_id) as unique_users
FROM user_coupons WHERE coupon_id = 'test-coupon-1';

결과:
total_records: 46,911
unique_users: 46,911
중복 발급: 0건
```

#### 에러율 분석

**25.93% 에러의 정상성:**
- 총 요청: 63,331건
- 성공: 46,936건 (74.07%)
- 중복 시도 에러: 16,395건 (25.93%)

**에러 발생 원인:**
- Random 방식으로 100,000명 중 랜덤 선택
- 같은 사용자가 여러 번 요청 시도 가능
- 시스템이 16,395건의 중복 시도를 정상적으로 차단

**결론:**
- 에러율 25.93%는 중복 발급 방지 로직이 정상 작동한다는 증거
- DB 검증 결과 실제 중복 발급은 0건
- Redis 캐시 + MySQL 이중 검증이 완벽하게 작동

#### 동시성 제어 메커니즘

**기술 스택:**
- Redis 분산 락 (Redisson)
- MySQL 유니크 제약 조건
- Kafka 비동기 처리

**처리 흐름:**
1. Redis 분산 락 획득 시도
2. DB에서 발급 여부 확인
3. Kafka로 발급 이벤트 발행
4. Consumer가 실제 쿠폰 발급 처리

**검증 완료:**
- 중복 발급: 0건
- 동시성 제어: 완벽
- 비동기 처리: 정상

---

### 4.2 잔액 충전 API

#### 테스트 시나리오

**부하 패턴:**
```javascript
scenarios: {
  concurrent_charge_test: {
    executor: 'constant-vus',
    vus: 100,
    duration: '1m',
  },
  load_test: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '1m', target: 150 },
      { duration: '30s', target: 0 },
    ],
    startTime: '2m',
  },
}
```

**테스트 전략:**
- Concurrent Test: 동일 사용자에 대한 동시 충전 시도 (10명 유저에 집중)
- Load Test: 일반적인 부하 상황 (100명 유저 랜덤)
- 총 5분 동안 진행

#### 초기 문제 상황

**테스트 결과 (수정 전):**
```
총 요청: 153,763건
에러율: 26.78% (41,186건 실패)
HTTP 실패율: 0%
성공한 충전: 112,577건
```

**문제 원인 분석:**

k6 테스트 스크립트와 API 응답 필드명 불일치:

**k6 검증 코드:**
```javascript
'response has balance': (r) => {
  try {
    const body = JSON.parse(r.body);
    return body.balance !== undefined;  // 잘못된 필드명
  } catch (e) {
    return false;
  }
}
```

**실제 API 응답:**
```java
public record ChargeBalanceResponse(
    String userId,
    Long previousBalance,
    Long chargedAmount,
    Long currentBalance,     // 실제 필드명
    LocalDateTime chargedAt
)
```

**문제:**
- k6가 `balance` 필드를 찾으려 했으나 실제로는 `currentBalance` 존재
- HTTP 요청은 200 OK로 성공했지만 필드 검증 실패
- 41,186건의 체크 실패 발생 (concurrentChargeTest 시나리오 전체)

#### 문제 해결

**수정 사항:**

1. k6 응답 검증 로직 수정:
```javascript
// Before
return body.balance !== undefined;

// After
return body.currentBalance !== undefined;
```

2. 콘솔 로그 출력 수정:
```javascript
// Before
console.log(`[충전 성공] balance: ${data.balance}`);

// After
console.log(`[충전 성공] balance: ${data.currentBalance}`);
```

3. Summary 함수 통계 수정:
```javascript
// Before
- p(50): ${...['p(50)']...}ms  // 존재하지 않음
- p(99): ${...['p(99)']...}ms  // 존재하지 않음

// After
- p(50): ${...med...}ms        // 실제 중앙값
- p(90): ${...['p(90)']...}ms  // 추가
```

#### 최종 테스트 결과

**핵심 지표:**

| 메트릭 | 결과 | 목표 | 상태 |
|--------|------|------|------|
| 총 요청 수 | 166,759건 | - | - |
| 평균 TPS | 551.59 req/s | 300+ | 달성 |
| 평균 응답 시간 | 90.21ms | <150ms | 달성 |
| p(50) 응답 시간 | 68.25ms | - | 우수 |
| p(90) 응답 시간 | 261.08ms | - | 양호 |
| p(95) 응답 시간 | 287.65ms | <1500ms | 달성 |
| 최대 응답 시간 | 2035.99ms | <3000ms | 달성 |
| 에러율 | 0.00% | <5% | 완벽 |
| HTTP 실패율 | 0% | <5% | 완벽 |
| 성공한 충전 | 128,873건 | - | - |
| 동시성 충돌 | 0건 | 0 | 완벽 |

**개선 전후 비교:**

| 항목 | 수정 전 | 수정 후 | 개선율 |
|------|---------|---------|--------|
| 에러율 | 26.78% | 0.00% | 100% 개선 |
| 체크 성공률 | 73.22% | 100% | 26.78%p 향상 |
| 성공한 충전 | 112,577건 | 128,873건 | 14.5% 증가 |

#### 동시성 제어 검증

**Concurrent Charge Test 결과:**
- VU: 100명
- 유저 풀: 10명 (동일 유저에 대한 충돌 유도)
- 동시성 충돌: 0건
- 결론: Pessimistic Lock 완벽 작동

**Lock 메커니즘:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.id = :userId")
User findByIdWithLock(@Param("userId") String userId);
```

**검증 완료:**
- 동일 사용자의 동시 충전 요청 안전하게 처리
- Lock 대기 시간으로 인한 응답 지연 최소화 (최대 2초)
- 데이터 정합성 100% 보장

---

### 4.3 주문/결제 API

#### 테스트 시나리오

**부하 패턴:**
```javascript
scenarios: {
  load_test: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '30s', target: 0 },
    ],
  },
}
```

**테스트 전략:**
- Warm-up: 30초 동안 50 VU까지 증가
- Peak: 1분 동안 100 VU 유지
- Cool-down: 30초 동안 0 VU로 감소
- 총 2분 동안 진행

#### 초기 문제 상황

**테스트 결과 (최적화 전):**
```
총 요청 수: 901건
평균 TPS: 7.54
성공한 주문: 824건
성공률: 91.45%

응답 시간:
  - 평균: 162ms
  - p(95): 404ms
  - p(99): 632ms

에러 통계:
  - 전체 에러율: 8.55%
  - 잔액 부족 에러: 45
  - 재고 부족 에러: 32
```

**발견된 문제:**

**문제 1: TPS 7.54 (목표 50 대비 85% 미달)**
- 100명이 동시 접속해도 초당 7.54건만 처리
- 실제 서비스 불가능한 수준의 성능

**문제 2: 전역 분산 락으로 인한 병목**
```java
@DistributedLock(key = "'order:global'")  // 모든 주문이 동일 락 사용
public OrderResponse execute(CreateOrderRequest request) {
    return createOrderService.create(request);
}
```

**원인:**
- 모든 사용자의 주문이 `LOCK:order:global` 하나를 공유
- 100명이 순차적으로 처리됨 (동시 처리 능력 = 1)
- Lock 대기 시간 누적으로 응답 시간 증가

**문제 3: Full Table Scan으로 인한 장바구니 조회 지연**
```sql
EXPLAIN SELECT * FROM cart_items WHERE cart_id = 'test-cart-1';
```

| id | type | key | rows | Extra |
|----|------|-----|------|-------|
| 1 | ALL | NULL | 100,000 | Using where |

**원인:**
- `cart_items` 테이블에 `cart_id` 인덱스 누락
- 100,000개 전체 레코드를 스캔
- 장바구니 조회에 60ms 소요 (전체 처리 시간의 88%)

**문제 4: Connection Pool 부족**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # 기본값
```

**원인:**
- 100 VU 환경에서 10개 Connection 경쟁
- 90개 요청은 Connection 대기 발생
- 평균 50ms의 추가 대기 시간 발생

#### 성능 최적화 과정

**최적화 1: 전역 락 → 사용자별 락 전환**

```java
// Before
@DistributedLock(key = "'order:global'")

// After
@DistributedLock(key = "'order:user:' + #request.userId")
```

**효과:**
- 동시 처리 능력: 1명 → 100명
- Lock 충돌: 동일 사용자의 중복 요청에만 발생
- 이론적 최대 TPS: 7.7 → 770

**안전성 보장 (Defense in Depth):**
```java
// DB 레벨 재고 검증
@Query("SELECT s FROM Stock s WHERE s.productId = :productId FOR UPDATE")
Stock findByIdWithLock(@Param("productId") String productId);
```

**최적화 2: 인덱스 추가**

```java
// Before
@Table(name = "cart_items")

// After
@Table(name = "cart_items", indexes = {
    @Index(name = "idx_cart_id", columnList = "cart_id")
})
```

**효과:**
- 쿼리 실행 시간: 60ms → 2ms (30배 개선)
- 주문 처리 시간: 68ms → 10ms (85% 감소)
- Full Table Scan 제거

**최적화 3: Connection Pool 확대**

```yaml
# Before
maximum-pool-size: 10

# After
maximum-pool-size: 100
minimum-idle: 20
connection-timeout: 30000
idle-timeout: 600000
max-lifetime: 1800000
```

**효과:**
- Connection 대기 시간: 50ms → 0ms
- p(95) 응답 시간: 404ms → 131ms (67% 개선)
- p(99) 응답 시간: 632ms → 245ms (61% 개선)

#### 최종 테스트 결과

**핵심 지표:**

| 메트릭 | 결과 | 목표 | 상태 |
|--------|------|------|------|
| 총 요청 수 | 9,801건 | - | - |
| 평균 TPS | 81.68 req/s | 50+ | 달성 (163%) |
| 평균 응답 시간 | 72ms | <150ms | 달성 |
| p(95) 응답 시간 | 131ms | <2000ms | 달성 |
| p(99) 응답 시간 | 245ms | <3000ms | 달성 |
| 에러율 | 8.62% | <15% | 달성 |
| 성공한 주문 | 8,956건 | - | - |
| 성공률 | 91.38% | >85% | 달성 |

**개선 전후 비교:**

| 지표 | 최적화 전 | 최적화 후 | 개선율 |
|------|----------|----------|--------|
| TPS | 7.54 | 81.68 | 10.8배 |
| 총 요청 수 | 901 | 9,801 | 10.9배 |
| 성공 주문 수 | 824 | 8,956 | 10.9배 |
| 평균 응답 시간 | 162ms | 72ms | 56% 개선 |
| p(95) 응답 시간 | 404ms | 131ms | 67% 개선 |
| p(99) 응답 시간 | 632ms | 245ms | 61% 개선 |
| 동시 처리 능력 | 1명 | 100명 | 100배 |

#### 비즈니스 임팩트

**1) 실제 트래픽 대응 가능**
- 점심/저녁 피크 타임 (100-200 TPS) 대응 가능
- 특가 이벤트 시에도 안정적인 서비스 제공

**2) 사용자 경험 개선**
- 평균 응답 시간 162ms → 72ms (55% 개선)
- 대부분의 주문이 131ms 이내 완료 (p95)

**3) 비용 절감**
- 동일 하드웨어에서 10.8배 처리량 달성
- Scale-up/Scale-out 전 소프트웨어 최적화로 해결

---

## 5. 종합 분석

### 5.1 API별 성능 비교

| API | TPS | 평균 응답 | p(95) 응답 | 에러율 | 동시성 제어 | 상태 |
|-----|-----|----------|-----------|--------|------------|------|
| 쿠폰 발급 | 372.32 | 29.84ms | 96.69ms | 25.93% | 완벽 (중복 0건) | 우수 |
| 잔액 충전 | 551.59 | 90.21ms | 287.65ms | 0.00% | 완벽 (충돌 0건) | 우수 |
| 주문/결제 | 81.68 | 72ms | 131ms | 8.62% | 완벽 (검증 완료) | 우수 |

### 5.2 동시성 제어 전략 비교

| API | 동시성 제어 방식 | Lock 범위 | 성능 | 안전성 |
|-----|----------------|----------|------|--------|
| 쿠폰 발급 | Redis 분산 락 + Kafka | 전역 | 372 TPS | 중복 발급 0건 |
| 잔액 충전 | Pessimistic Lock | 사용자별 | 551 TPS | 동시성 충돌 0건 |
| 주문/결제 | 사용자별 분산 락 + DB Lock | 사용자별 + 상품별 | 81 TPS | 재고 정합성 보장 |

### 5.3 주요 개선 사항 요약

**쿠폰 발급 API:**
- 중복 발급 방지 로직 완벽 작동 검증
- Random 방식 테스트로 실제 트래픽 패턴 재현
- 에러율 25.93%는 중복 시도 차단의 증거

**잔액 충전 API:**
- API 응답 필드와 테스트 검증 로직 정합성 확보
- k6 메트릭 구조 정확히 파악 및 활용
- Pessimistic Lock의 동시성 제어 효과 검증

**주문/결제 API:**
- Lock Granularity 최적화로 TPS 10.8배 향상
- DB 인덱스 추가로 쿼리 성능 30배 개선
- Connection Pool 확대로 응답 시간 67% 개선

### 5.4 공통 교훈

**1) Lock Granularity가 성능의 핵심**
- 전역 락은 동시성을 심각하게 저하시킴
- 필요한 최소 범위로 Lock을 제한해야 함
- 사용자별, 리소스별 Lock으로 동시 처리 능력 극대화

**2) DB 인덱스는 성능의 기본**
- 인덱스 하나로 쿼리 성능 30배 개선 가능
- 개발 단계부터 실행 계획 분석 필수
- Full Table Scan은 반드시 제거해야 함

**3) Connection Pool은 동시성에 비례**
- 예상 동시 접속자 수에 맞춰 설정 필요
- minimum-idle 설정으로 Cold Start 방지
- Connection 대기가 전체 응답 시간에 영향

**4) 부하 테스트는 필수**
- 로컬 테스트로는 동시성 문제를 발견할 수 없음
- 실제 트래픽 패턴 시뮬레이션 필요
- 메트릭 기반 분석으로 병목 지점 정확히 식별

**5) API 스펙과 테스트 코드의 정합성**
- 응답 DTO 변경 시 테스트 코드 동시 업데이트
- 필드명 불일치가 거짓 에러로 이어짐
- 타입스크립트 인터페이스 정의로 사전 방지 가능

### 5.5 성능 기준선 수립

본 부하 테스트를 통해 각 API의 성능 기준선을 확립했습니다:

**쿠폰 발급 API 기준선:**
- TPS: 372 req/s (목표: >200)
- p(95): 96ms (임계값: <1000ms)
- 중복 발급: 0건 (임계값: 0건)

**잔액 충전 API 기준선:**
- TPS: 551 req/s (목표: >300)
- p(95): 287ms (임계값: <1500ms)
- 동시성 충돌: 0건 (임계값: 0건)

**주문/결제 API 기준선:**
- TPS: 81 req/s (목표: >50)
- p(95): 131ms (임계값: <2000ms)
- 에러율: 8.62% (임계값: <15%)

---

## 6. 결론 및 향후 계획

### 6.1 결론

본 프로젝트를 통해 E-commerce 시스템의 3개 핵심 API에 대한 부하 테스트를 설계하고 수행하여, 실제 운영 환경에서 발생할 수 있는 성능 문제를 사전에 발견하고 개선했습니다.

**주요 성과:**

1. **쿠폰 발급 API**: TPS 372, 중복 발급 0건으로 완벽한 동시성 제어 검증
2. **잔액 충전 API**: TPS 551, 에러율 0%로 높은 안정성 확인
3. **주문/결제 API**: TPS 10.8배 향상으로 실제 서비스 가능 수준 달성

**핵심 개선:**

1. Lock Granularity 최적화로 동시 처리 능력 100배 향상
2. DB 인덱스 추가로 쿼리 성능 30배 개선
3. Connection Pool 확대로 응답 시간 67% 개선
4. API 스펙과 테스트 코드 정합성 확보로 정확한 검증

**검증 완료:**

1. 동시성 제어: 중복 발급 0건, 동시성 충돌 0건
2. 응답 성능: 모든 API가 목표 응답 시간 달성
3. 처리량: 모든 API가 목표 TPS 달성
4. 데이터 정합성: DB 검증을 통한 무결성 보장

### 6.2 향후 계획

**1) 추가 테스트 시나리오**

**Spike Test:**
- 급격한 트래픽 증가 대응 능력 검증
- 이벤트 시작 시점 시뮬레이션
- Auto-scaling 트리거 포인트 측정

**Soak Test:**
- 장시간(1시간 이상) 안정성 검증
- 메모리 누수, Connection 누수 감지
- GC 튜닝 필요성 판단

**Stress Test:**
- 시스템 한계 지점 측정
- 장애 발생 시점 및 복구 능력 확인
- Graceful Degradation 전략 수립

**복합 시나리오:**
- 쿠폰 발급 + 주문 + 결제 동시 진행
- 실제 사용자 여정 시뮬레이션
- End-to-End 성능 측정

**2) 모니터링 강화**

**APM 도구 도입:**
- Pinpoint, Zipkin 등 분산 트레이싱
- 실시간 병목 지점 모니터링
- 프로덕션 환경 성능 이슈 빠른 탐지

**메트릭 수집 및 시각화:**
- Prometheus + Grafana 연동
- TPS, 응답 시간, 에러율 실시간 차트
- Lock 대기 시간, Connection Pool 사용률 모니터링

**알림 설정:**
- p(95) > 목표값: 경고
- 에러율 > 임계값: 위험
- TPS < 기준선: 성능 저하 감지

**3) 아키텍처 개선**

**Read Replica 도입:**
- 읽기 작업을 Replica로 분산
- Write Master와 Read Replica 분리
- 장바구니 조회, 상품 조회 등 부하 분산

**CQRS 패턴 적용:**
- Command (주문 생성): Write DB
- Query (주문 조회): Read DB 또는 Redis Cache
- 읽기/쓰기 최적화 분리

**비동기 처리 확대:**
- 상품 랭킹 업데이트 비동기 전환
- 트랜잭션 시간 단축
- Outbox Pattern 활용 범위 확대

**4) 테스트 자동화**

**CI/CD 통합:**
- GitHub Actions에 k6 테스트 추가
- PR 생성 시 자동 성능 테스트
- 성능 회귀 방지

**성능 회귀 테스트:**
- 배포 전 자동 부하 테스트
- 기준선 대비 성능 비교
- 성능 저하 시 배포 중단

**결과 아카이빙:**
- 테스트 결과 히스토리 관리
- 성능 트렌드 분석
- 개선 효과 추적

---

## 부록

### A. 참고 문서

**상세 보고서:**
- `K6_LOAD_TEST_REPORT.md`: 쿠폰 발급 API 테스트 상세 보고서
- `K6_BALANCE_CHARGE_TEST_REPORT.md`: 잔액 충전 API 테스트 상세 보고서
- `PERFORMANCE_OPTIMIZATION_REPORT.md`: 주문/결제 API 최적화 상세 보고서

**테스트 스크립트:**
- `k6-tests/balance-charge-test.js`: 잔액 충전 부하 테스트 스크립트
- `k6-tests/order-payment-test.js`: 주문/결제 부하 테스트 스크립트

**데이터 시딩:**
- `src/test/java/com/sparta/ecommerce/LoadTestDataSeeder.java`: 테스트 데이터 생성 시더

### B. 실행 명령어

**1. 데이터 생성 (100,000명 유저)**
```bash
./gradlew test --tests "LoadTestDataSeeder.seedForLoadTest"
```

**2. 쿠폰 발급 부하 테스트**
```bash
k6 run k6-tests/coupon-issue-test.js
```

**3. 잔액 충전 부하 테스트**
```bash
k6 run k6-tests/balance-charge-test.js
```

**4. 주문/결제 부하 테스트**
```bash
k6 run k6-tests/order-payment-test.js
```

**5. 환경변수 지정 실행**
```bash
BASE_URL=http://localhost:8081 k6 run k6-tests/order-payment-test.js
```

**6. JSON 결과 저장**
```bash
k6 run --out json=result.json k6-tests/order-payment-test.js
```

### C. 데이터베이스 검증 쿼리

**쿠폰 발급 검증:**
```sql
-- 쿠폰 상태 확인
SELECT id, total_quantity, issued_quantity, remaining_quantity
FROM coupons WHERE id = 'test-coupon-1';

-- 중복 발급 검증
SELECT COUNT(*) as total_records, COUNT(DISTINCT user_id) as unique_users
FROM user_coupons WHERE coupon_id = 'test-coupon-1';
```

**잔액 충전 검증:**
```sql
-- 총 충전 금액 확인
SELECT COUNT(*) as total_charges, SUM(amount) as total_amount
FROM balance_charge_history;

-- 사용자별 충전 이력
SELECT user_id, COUNT(*) as charge_count, SUM(amount) as total_charged
FROM balance_charge_history
GROUP BY user_id
ORDER BY charge_count DESC
LIMIT 10;
```

**주문/결제 검증:**
```sql
-- 주문 통계
SELECT COUNT(*) as total_orders, SUM(final_amount) as total_revenue
FROM orders;

-- 재고 차감 검증
SELECT product_id, SUM(quantity) as total_sold
FROM order_items
GROUP BY product_id;
```

---

**작성일**: 2025-12-26
**작성자**: 조솔하
**프로젝트**: E-commerce Core System
**테스트 환경**: Spring Boot 3.5.7, MySQL 8.0, Redis 7.2, k6 v0.54.0
**시스템 상태**: 운영 준비 완료
**다음 단계**: Spike/Soak/Stress Test 추가, APM 도구 도입, Read Replica 구축
