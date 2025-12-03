# 인기 상품 조회 성능 비교

## 📊 개요

인기 상품 조회 기능의 3가지 구현 방식을 비교하고, 각 방식의 성능과 특징을 분석합니다.

---

## 🔍 테스트 환경

- **데이터**: 최근 3일간의 실제 주문 데이터
- **조회 조건**: Top 5 인기 상품
- **측정 지표**: 응답 시간 (ms)
- **테스트 클래스**:
  - `CachePerformanceTest` (Step 12 캐시 방식)
  - `RankingPerformanceComparisonTest` (Step 13 Redis 랭킹 방식)

---

## 📈 성능 측정 결과

### 방식 1: DB 집계 쿼리 (Step 11 이전)

```sql
SELECT p.*, SUM(oi.quantity) as sales_count
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
JOIN products p ON oi.product_id = p.id
WHERE o.created_at >= ? AND o.status = 'COMPLETED'
GROUP BY p.id
ORDER BY sales_count DESC, MAX(o.created_at) DESC
LIMIT 5
```

**측정 결과:**
- **응답 시간**: 17,520ms (17.5초)
- **DB 부하**: 매우 높음 (전체 스캔 + JOIN + GROUP BY)

---

### 방식 2: Spring Cache (Step 12)

**캐시 적용 전:**
```java
@Cacheable(
    value = "popularProducts",
    key = "'top5:' + #days",
    cacheManager = "cacheManager"
)
public List<PopularProductResponse> execute(int days, int limit)
```

**측정 결과:**

| 상태 | 응답 시간 | 설명 |
|------|----------|------|
| **Cache Miss** | 120ms | 첫 조회 시 DB 집계 후 캐싱 |
| **Cache Hit** | 6.5ms | 캐시에서 직접 조회 |

**개선율**: 95% (120ms → 6.5ms)

**TTL**: 5분

---

### 방식 3: Redis Sorted Set 실시간 랭킹 (Step 13)

**구조:**
```
product:ranking:2025-12-04
  - productId1: 70 (판매량)
  - productId2: 51
  - productId3: 50
  ...
```

**측정 결과:**
- **응답 시간**: 697ms
  - Redis 랭킹 조회: 157ms
  - DB 상품 정보 조회: 439ms (PK 기반)
  - 기타 처리: 101ms

**주문 시 랭킹 업데이트**: ~1ms (비동기)

---

## 📊 종합 비교표

| 구분 | DB 집계 | Spring Cache | Redis 랭킹 |
|------|---------|--------------|------------|
| **첫 조회** | 17,520ms | 120ms | 697ms |
| **이후 조회** | 17,520ms | 6.5ms | 697ms |
| **실시간성** | ✅ 즉시 | ❌ 5분 지연 | ✅ 즉시 |
| **DB 부하** | 🔴 매우 높음 | 🟡 주기적 발생 | 🟢 낮음 (PK 조회만) |
| **안정성** | 🔴 낮음 | 🟡 중간 | 🟢 높음 |
| **확장성** | 🔴 나쁨 | 🟡 보통 | 🟢 좋음 |
| **구현 복잡도** | 🟢 낮음 | 🟢 낮음 | 🟡 중간 |

---

## 🎯 상세 분석

### 1️⃣ DB 집계 방식

#### 장점
- ✅ 구현이 간단함
- ✅ 추가 인프라 불필요
- ✅ 항상 최신 데이터 반영

#### 단점
- ❌ 매우 느린 응답 속도 (17.5초)
- ❌ DB 부하 높음 (전체 테이블 스캔)
- ❌ 동시 요청 시 DB 과부하 위험
- ❌ 데이터 증가 시 성능 급격히 저하

#### 적합한 경우
- 데이터가 매우 적을 때 (수백 건 이하)
- 조회 빈도가 낮을 때
- 프로토타입 단계

---

### 2️⃣ Spring Cache 방식

#### 장점
- ✅ 캐시 히트 시 매우 빠름 (6.5ms)
- ✅ Spring Boot 통합 용이
- ✅ 구현 간단 (`@Cacheable` 하나)
- ✅ 코드 변경 최소화

#### 단점
- ❌ 캐시 만료 시 DB 부하 발생 (120ms)
- ❌ TTL 동안 실시간 반영 안 됨
- ❌ Cold Start 시 느림
- ❌ 캐시 워밍업 필요

#### 적합한 경우
- 실시간성이 중요하지 않을 때 (5분 지연 허용)
- 트래픽이 집중된 시간대가 있을 때
- 빠른 캐시 조회가 최우선일 때

#### 주의사항
```java
// TTL 5분 설정
RedisCacheConfiguration.defaultCacheConfig()
    .entryTtl(Duration.ofMinutes(5))
```
- TTL이 짧으면 Cache Miss 빈번 → DB 부하
- TTL이 길면 실시간성 저하

---

### 3️⃣ Redis Sorted Set 실시간 랭킹 방식

#### 장점
- ✅ 실시간 랭킹 반영 (주문 즉시)
- ✅ 안정적인 응답 속도 (~700ms)
- ✅ DB 부하 최소화 (PK 조회만)
- ✅ 데이터 증가해도 성능 유지
- ✅ 확장성 우수 (Redis 분산 가능)
- ✅ Fallback 지원 (Redis 장애 시 DB 사용)

#### 단점
- ❌ 구현 복잡도 높음 (Redis 운영 필요)
- ❌ 캐시보다 느림 (697ms vs 6.5ms)
- ❌ Redis 메모리 사용
- ❌ 주문 시 Redis 업데이트 오버헤드

#### 적합한 경우
- 실시간 랭킹이 중요할 때
- 대용량 트래픽 처리
- 안정적인 성능이 필요할 때
- E-commerce 실시간 인기 상품

#### 구현 핵심
```java
// 주문 완료 시 랭킹 업데이트
public void incrementPurchaseCount(String productId) {
    String todayKey = "product:ranking:" + LocalDate.now();
    redisTemplate.opsForZSet()
        .incrementScore(todayKey, productId, 1.0);
}

// 최근 N일 Top 상품 조회
public List<RankingItem> getTopProducts(int days, int limit) {
    // ZUNIONSTORE로 여러 날짜 병합
    // ZREVRANGE로 상위 N개 조회
}
```

---

## 🚀 성능 개선 효과

### DB 집계 → Spring Cache
- **개선율**: 99.6% (17,520ms → 6.5ms, Cache Hit 기준)
- **효과**: 🔥🔥🔥 압도적 속도 향상

### DB 집계 → Redis 랭킹
- **개선율**: 96% (17,520ms → 697ms)
- **효과**: 🔥🔥 높은 속도 향상 + 실시간성

### Spring Cache → Redis 랭킹 (Cache Hit 기준)
- **속도**: 느려짐 (6.5ms → 697ms)
- **실시간성**: 개선 (5분 지연 → 즉시 반영)
- **안정성**: 개선 (Cold Start 이슈 해결)

---

## 💡 권장 사항

### 시나리오별 최적 선택

#### 🎯 실시간 랭킹이 중요한 경우
→ **Redis Sorted Set 방식** (Step 13)
- 예: 실시간 인기 상품, 급상승 검색어

#### 🎯 최고 속도가 중요한 경우
→ **Spring Cache 방식** (Step 12)
- 예: 일간/주간 베스트 상품 (실시간 불필요)

#### 🎯 초기 개발 단계
→ **DB 집계 방식**
- 빠른 프로토타이핑 후 나중에 개선

### 하이브리드 접근

**최적의 조합:**
```
Redis 랭킹 (실시간) + 상품 정보 캐싱
```

**예상 성능:**
- Redis 랭킹: 157ms
- 상품 정보 캐시 조회: 10ms
- **총 응답 시간**: ~170ms ⚡

**구현:**
```java
@Cacheable(value = "products", key = "#productId")
public Product findById(String productId) {
    return productRepository.findById(productId);
}
```

---

## 📝 결론

| 우선순위 | 선택 기준 | 추천 방식 |
|---------|----------|----------|
| 1순위 | 실시간성 | Redis 랭킹 |
| 2순위 | 최고 속도 | Spring Cache |
| 3순위 | 안정성 | Redis 랭킹 |
| 4순위 | 구현 용이성 | Spring Cache |
| 5순위 | 확장성 | Redis 랭킹 |

### 최종 추천

**프로덕션 환경**
- ✅ **Redis Sorted Set 실시간 랭킹** (Step 13)
- 이유: 실시간성 + 안정성 + 확장성

**캐시 보완**
- 상품 정보 캐싱 추가로 속도 개선
- 총 응답 시간: 697ms → 170ms 가능

---

## 📚 참고 자료

- 테스트 코드: `src/test/java/com/sparta/ecommerce/application/product/`
  - `CachePerformanceTest.java`
  - `RankingPerformanceComparisonTest.java`
- 구현 코드:
  - `GetPopularProductsUseCase.java`
  - `ProductRankingService.java`
- 캐시 설정: `CacheConfig.java`
- Redis 설정: `RedisConfig.java`

---

**작성일**: 2025-12-04
**테스트 환경**: Spring Boot 3.5.7, Redis 7.x, MySQL 8.4.7
