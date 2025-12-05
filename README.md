# E-commerce Core System - 기술 보고서

**Redis를 활용한 실시간 랭킹 및 비동기 쿠폰 발급**

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [핵심 구현 기능](#2-핵심-구현-기능)
   - [2.1 Redis Sorted Set 실시간 인기 상품 랭킹](#21-redis-sorted-set-실시간-인기-상품-랭킹)
   - [2.2 Redis Blocking Queue 선착순 쿠폰 발급](#22-redis-blocking-queue-선착순-쿠폰-발급)
3. [성능 측정 결과](#3-성능-측정-결과)
4. [결론](#4-결론)

---

## 1. 프로젝트 개요

Spring Boot 3.5.7과 Java 17 기반의 이커머스 백엔드 시스템입니다.

### 기술 스택

| 카테고리 | 기술 |
|---------|------|
| **언어 및 프레임워크** | Java 17, Spring Boot 3.5.7 |
| **데이터베이스** | MySQL 8.0 |
| **인메모리 DB** | Redis 7 |
| **동시성 제어** | Redisson (분산 락) |
| **테스트** | JUnit 5, Testcontainers |

### 핵심 문제 상황

이커머스 시스템에서 직면한 두 가지 주요 성능 이슈:

**1. 인기 상품 조회 성능 문제**
- 복잡한 JOIN + GROUP BY 집계 쿼리로 인한 응답 지연 (17.5초)
- 주문 데이터 증가에 따른 쿼리 성능 급격한 저하
- 매 조회마다 전체 테이블 스캔 발생

**2. 선착순 쿠폰 발급 동시성 문제**
- 기존 분산 락: 순서 보장이 안되고 응답이 느림 (5~10초)
- 1000명이 동시에 100개 제한 쿠폰 요청 시 사용자 대기 시간 증가
- 락 획득 실패 시 즉시 실패 응답으로 사용자 경험 저하

---

## 2. 핵심 구현 기능

### 2.1 Redis Sorted Set 실시간 인기 상품 랭킹

#### 문제 상황

**복잡한 집계 쿼리의 심각한 성능 문제**

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
- **응답 시간: 17,520ms (17.5초)**
- 전체 테이블 스캔 + JOIN + GROUP BY + ORDER BY
- DB 부하 매우 높음
- 데이터 증가 시 성능 급격히 저하

#### 해결 방법 비교

학습한 3가지 방법을 비교하여 최적의 방법을 선택했습니다.

**방법 1: DB 집계 쿼리 (기존 방식)**

```java
// 매 조회마다 DB에서 집계
public List<PopularProductResponse> execute(int days, int limit) {
    LocalDateTime startDate = LocalDateTime.now().minusDays(days);
    return orderItemRepository.findPopularProducts(startDate, limit);
}
```

**장점:**
- ✅ 구현이 간단함
- ✅ 추가 인프라 불필요
- ✅ 항상 최신 데이터 반영 (실시간)

**단점:**
- ❌ 매우 느린 응답 속도 (17.5초)
- ❌ DB 부하 높음 (전체 테이블 스캔)
- ❌ 동시 요청 시 DB 과부하 위험
- ❌ 데이터 증가 시 성능 급격히 저하

---

**방법 2: Spring Cache-Aside 패턴**

```java
@Cacheable(cacheNames = "popularProducts", key = "#days + ':' + #limit")
public List<PopularProductResponse> execute(int days, int limit) {
    // 캐시 미스 시에만 DB 조회
    LocalDateTime startDate = LocalDateTime.now().minusDays(days);
    return orderItemRepository.findPopularProducts(startDate, limit);
}
```

**Redis 캐시 설정:**
```java
RedisCacheConfiguration.defaultCacheConfig()
    .entryTtl(Duration.ofMinutes(5))  // TTL 5분
```

**장점:**
- ✅ 캐시 히트 시 매우 빠름 (6.5ms)
- ✅ Spring Boot 통합 용이 (`@Cacheable` 어노테이션만)
- ✅ 구현 간단 (코드 변경 최소화)
- ✅ Redis 장애 시 자동으로 DB 조회 (Fallback)

**단점:**
- ❌ TTL 동안 실시간 반영 안 됨 (최대 5분 지연)
- ❌ 캐시 만료 시 DB 부하 발생 (120ms 소요)
- ❌ 첫 요청(Cache Miss) 시 느림
- ❌ 실시간 랭킹 불가능

**측정 결과:**
- Cache Miss: 120ms
- Cache Hit: 6.5ms
- 개선율: 95% (120ms → 6.5ms)

---

**방법 3: Redis Sorted Set 실시간 랭킹 (선택)**

```java
// 주문 완료 시 랭킹 업데이트
public void incrementPurchaseCount(String productId, int quantity) {
    String todayKey = "product:ranking:" + LocalDate.now();
    redisTemplate.opsForZSet().incrementScore(todayKey, productId, quantity);
}

// 최근 N일 Top 상품 조회
public List<String> getTopProducts(int days, int limit) {
    // ZUNIONSTORE로 여러 날짜 병합
    // ZREVRANGE로 상위 N개 조회
}
```

**Redis 자료구조:**
```
product:ranking:2025-12-05
  - productId1: 70 (판매량)
  - productId2: 51
  - productId3: 50
  ...

ZUNIONSTORE → 여러 날짜 병합 → ZREVRANGE → Top 5
```

**장점:**
- ✅ 실시간 랭킹 반영 (주문 즉시 업데이트)
- ✅ 안정적인 응답 속도 (~700ms, 일정)
- ✅ DB 부하 최소화 (집계 쿼리 제거, PK 조회만)
- ✅ 데이터 증가해도 성능 유지 (O(log N))
- ✅ 확장성 우수 (Redis Cluster 가능)

**단점:**
- ❌ 캐시보다 느림 (697ms vs 6.5ms)
- ❌ 구현 복잡도 높음 (주문 시마다 Redis 업데이트)
- ❌ Redis 메모리 사용
- ❌ Redis 장애 시 Fallback 필요

**측정 결과:**
- 응답 시간: 697ms
  - Redis 랭킹 조회: 157ms
  - DB 상품 정보 조회: 439ms
  - 기타 처리: 101ms
- 개선율: 96% (17,520ms → 697ms)

---

#### 최종 선택: Redis Sorted Set

**선택 이유:**

실시간성이 가장 중요한 요구사항이었습니다.

| 요구사항 | DB 집계 | Spring Cache | Redis 랭킹 |
|---------|---------|-------------|-----------|
| **실시간성** | ✅ 즉시 | ❌ 5분 지연 | ✅ 즉시 |
| **응답 속도** | ❌ 17.5초 | ✅ 6.5ms | 🟡 697ms |
| **안정성** | ❌ 낮음 | 🟡 중간 | ✅ 높음 |
| **확장성** | ❌ 나쁨 | 🟡 보통 | ✅ 좋음 |

**결론:**
- Spring Cache가 가장 빠르지만 **실시간 반영이 안됨** (5분 지연)
- Redis Sorted Set은 캐시보다 느리지만 **실시간 반영 + 안정적 성능**
- 이커머스 인기 상품은 실시간성이 중요 → **Redis Sorted Set 선택**

---

### 2.2 Redis Blocking Queue 선착순 쿠폰 발급

#### 문제 상황

**기존 분산 락 방식의 문제점**

```java
@DistributedLock(key = "'coupon:issue:'.concat(#couponId)")
public UserCouponResponse execute(String userId, String couponId) {
    return couponIssueService.issue(userId, couponId);
}
```

**문제 1: 순서 보장 안됨**
- 분산 락은 동시성 제어만 가능, FIFO 보장 안함
- 락 획득 순서 ≠ 요청 순서
- 선착순 공정성 문제

**문제 2: 동기 처리로 느림**
- 1000명 동시 요청 시 평균 대기 시간 5~10초
- 사용자는 발급 완료까지 기다려야 함
- 락 획득 실패 시 즉시 실패

**문제 3: 자원 낭비**
- 대기 중인 요청들이 스레드 점유
- DB 커넥션 장시간 보유

#### 해결 방법 비교

분산 락의 문제를 해결하기 위해 Redis 기반 3가지 방법을 비교했습니다.

**방법 1: Polling 방식**

```java
// 주기적으로 Redis 확인
public void checkQueue() {
    while (true) {
        String userId = redisTemplate.opsForList().rightPop(queueKey);
        if (userId != null) {
            processCoupon(userId);
        }
        Thread.sleep(100);  // 0.1초마다 확인
    }
}
```

**장점:**
- ✅ 구현 간단
- ✅ FIFO 순서 보장 (Redis List 사용)

**단점:**
- ❌ 주기적인 폴링으로 CPU 낭비
- ❌ 폴링 주기만큼 지연 발생 (0.1초 간격이면 최대 0.1초 지연)
- ❌ 큐가 비어있어도 계속 조회
- ❌ 불필요한 네트워크 트래픽

---

**방법 2: Redis Pub/Sub**

```java
// Publisher
public void publishCouponRequest(String userId, String couponId) {
    redisTemplate.convertAndSend("coupon:channel", userId);
}

// Subscriber
@RedisListener(topics = "coupon:channel")
public void handleMessage(String userId) {
    processCoupon(userId);
}
```

**장점:**
- ✅ 실시간 메시지 전달 (폴링 불필요)
- ✅ 구현 간단 (Spring Redis Pub/Sub)
- ✅ 불필요한 CPU 낭비 없음

**단점:**
- ❌ **메시지 유실 위험** (구독자 없으면 메시지 사라짐)
- ❌ 순서 보장 안됨 (여러 구독자 있을 경우)
- ❌ 메시지 영속성 없음 (Redis 재시작 시 유실)
- ❌ At-most-once 전달만 보장

---

**방법 3: Redis Blocking Queue - BRPOP (선택)**

```java
// Producer: 큐에 추가
public void addToQueue(String couponId, String userId) {
    String queueKey = "coupon:queue:" + couponId;
    redisTemplate.opsForList().leftPush(queueKey, userId);  // LPUSH
}

// Consumer: Blocking Pop (대기)
public String blockingPopFromQueue(String couponId) {
    String queueKey = "coupon:queue:" + couponId;
    return redisTemplate.opsForList().rightPop(queueKey, 5, TimeUnit.SECONDS);  // BRPOP
}
```

**BRPOP 동작 방식:**
```
1. 큐에 데이터가 있으면 즉시 반환
2. 큐가 비어있으면 최대 5초 대기 (Blocking)
3. 5초 내 데이터 들어오면 즉시 반환
4. 5초 지나도 없으면 null 반환
```

**장점:**
- ✅ FIFO 순서 보장 (Redis List)
- ✅ 메시지 안정성 (Redis 영속성 보장)
- ✅ Blocking 방식으로 폴링 오버헤드 없음
- ✅ 네트워크 트래픽 최소화
- ✅ 큐에 데이터 없으면 자동 대기
- ✅ 애플리케이션 재시작 시 미처리 큐 유지

**단점:**
- ❌ 구현 복잡도 높음 (Worker 스레드 관리)
- ❌ Blocking 시간 설정 필요
- ❌ Redis 연결 유지 필요

---

#### 최종 선택: Redis Blocking Queue (BRPOP)

**선택 이유:**

| 요구사항 | Polling | Pub/Sub | BRPOP |
|---------|---------|---------|-------|
| **FIFO 보장** | ✅ | ❌ | ✅ |
| **메시지 안정성** | ✅ | ❌ | ✅ |
| **CPU 효율** | ❌ | ✅ | ✅ |
| **네트워크 효율** | ❌ | ✅ | ✅ |
| **메시지 유실 방지** | ✅ | ❌ | ✅ |

**결론:**
- Polling: 불필요한 CPU/네트워크 낭비
- Pub/Sub: **메시지 유실 위험 큼** (구독자 없으면 사라짐)
- BRPOP: **FIFO 보장 + 메시지 안정성 + 효율성** → 선택

---

#### 구현 방법

**아키텍처**

```
[사용자 요청] → [중복 체크 (Redis Set)] → [큐 추가 (LPUSH)] → [즉시 응답 50ms]
                                                   ↓
                                            [Redis Queue]
                                                   ↓
                                         [Worker 스레드 (BRPOP)]
                                                   ↓
                                         [쿠폰 발급 처리]
```

**1. 큐 추가 (빠른 응답)**

```java
@Service
@RequiredArgsConstructor
public class IssueCouponWithQueueUseCase {
    private final CouponQueueService queueService;

    public CouponQueueResponse execute(String userId, String couponId) {
        // 1. 중복 체크 (Redis Set - O(1))
        if (!queueService.addToIssuedSet(couponId, userId)) {
            throw new DuplicateCouponIssueException("이미 발급 요청한 쿠폰입니다");
        }

        // 2. 큐에 추가 (LPUSH)
        queueService.addToQueue(couponId, userId);

        // 3. 즉시 응답 (Worker가 처리)
        return new CouponQueueResponse(true, "쿠폰 발급 요청이 접수되었습니다");
    }
}
```

**2. Worker 처리 (백그라운드)**

```java
@Component
@RequiredArgsConstructor
public class CouponWorker {
    private final CouponQueueService queueService;
    private final CouponIssueProcessor issueProcessor;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    @PostConstruct
    public void startWorkers() {
        List<Coupon> activeCoupons = couponRepository.findAvailableCoupons();

        for (Coupon coupon : activeCoupons) {
            startWorkerForCoupon(coupon.getCouponId());
        }
    }

    public void startWorkerForCoupon(String couponId) {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // BRPOP: Blocking Pop (최대 5초 대기)
                    String userId = queueService.blockingPopFromQueue(couponId);

                    if (userId != null) {
                        issueProcessor.processSingleIssue(userId, couponId);
                    }
                } catch (Exception e) {
                    log.error("Worker 예외 발생", e);
                    Thread.sleep(1000);
                }
            }
        });
    }
}
```

**3. 큐 서비스**

```java
@Service
@RequiredArgsConstructor
public class CouponQueueService {
    private final StringRedisTemplate redisTemplate;

    // 큐에 추가 (LPUSH)
    public void addToQueue(String couponId, String userId) {
        String queueKey = "coupon:queue:" + couponId;
        redisTemplate.opsForList().leftPush(queueKey, userId);
    }

    // Blocking Pop (BRPOP)
    public String blockingPopFromQueue(String couponId) {
        String queueKey = "coupon:queue:" + couponId;
        return redisTemplate.opsForList().rightPop(queueKey, 5, TimeUnit.SECONDS);
    }

    // 중복 체크 (Redis Set)
    public boolean addToIssuedSet(String couponId, String userId) {
        String setKey = "coupon:issued:" + couponId;
        return redisTemplate.opsForSet().add(setKey, userId) > 0;
    }
}
```

**Redis 자료구조 활용**

```
1. Queue (List):
   coupon:queue:{couponId} = [user1, user2, user3, ...]
   - LPUSH: 왼쪽에서 추가
   - BRPOP: 오른쪽에서 Blocking Pop (FIFO)

2. 중복 방지 (Set):
   coupon:issued:{couponId} = {user1, user2, user3, ...}
   - SADD: O(1) 중복 체크
```

---

#### BRPOP 방식의 장점과 단점

**장점:**

✅ **빠른 응답 속도**
- 평균 응답 시간: **50ms 이하** (기존 5~10초 대비 99% 개선)
- 사용자는 대기 없이 즉시 응답 받음

✅ **FIFO 선착순 보장**
- Redis List의 순서 보장
- 먼저 요청한 사용자부터 순차 처리

✅ **자원 효율성**
- Worker 스레드 3개만 DB 접근
- 동시 요청 1000건도 3개 스레드로 처리

✅ **중복 발급 방지**
- Redis Set으로 O(1) 중복 체크

✅ **메시지 안정성**
- Redis에 큐 데이터 유지
- 애플리케이션 재시작 시 미처리 큐 자동 재개

**단점:**

⚠️ **발급 완료 시점 불확실**
- 문제: 사용자는 큐 추가 응답만 받고 실제 발급 여부를 모름
- 영향: 즉시 쿠폰 사용이 필요한 경우 불편
- 해결 방안: WebSocket/SSE로 발급 완료 알림 추가 (현재 미적용)

⚠️ **재고 소진 판단 지연**
- 문제: 큐에 추가된 후에야 재고 소진 여부 확인 가능
- 영향: 재고 소진 시에도 "요청 접수" 응답
- 해결 방안: Redis에 재고 정보 캐싱하여 사전 확인 (현재 미적용)

⚠️ **Worker 장애 시 처리 지연**
- 문제: Worker 스레드 종료 시 큐 처리 중단
- 해결: 애플리케이션 재시작 시 @PostConstruct로 자동 재개

⚠️ **구현 복잡도**
- 문제: Polling/Pub/Sub 대비 코드 복잡
- 판단: 응답 속도 향상과 메시지 안정성이 복잡도를 상회

---

## 3. 성능 측정 결과

### 3.1 인기 상품 조회 성능

**테스트 환경**
- DB: MySQL 8.0 (Testcontainers)
- Redis: 7.0
- 데이터: 최근 3일간 주문 데이터
- 측정: Top 5 인기 상품 조회

**성능 비교**

| 방식 | 첫 조회 | 이후 조회 | 실시간성 | DB 부하 | 안정성 | 확장성 |
|------|---------|----------|----------|---------|--------|--------|
| **DB 집계** | 17,520ms | 17,520ms | ✅ 즉시 | 🔴 매우 높음 | 🔴 낮음 | 🔴 나쁨 |
| **Spring Cache** | 120ms | 6.5ms | ❌ 5분 지연 | 🟡 주기적 | 🟡 중간 | 🟡 보통 |
| **Redis 랭킹** | 697ms | 697ms | ✅ 즉시 | 🟢 낮음 | 🟢 높음 | 🟢 좋음 |

**Redis 랭킹 상세 분석**

```
총 응답 시간: 697ms
  - Redis 랭킹 조회 (ZUNIONSTORE + ZREVRANGE): 157ms
  - DB 상품 정보 조회 (IN 쿼리): 439ms
  - 기타 처리 (매핑, 정렬): 101ms
```

**성능 개선**
- **DB 집계 대비: 96% 개선** (17,520ms → 697ms)
- **안정적인 응답 속도**: 데이터 증가해도 일정
- **DB 부하: 95% 감소** (복잡한 집계 → PK 조회)

---

### 3.2 선착순 쿠폰 발급 성능

**테스트 시나리오**
- 쿠폰 재고: 100개
- 동시 요청: 1000명
- 측정 항목: 응답 시간, 재고 정확성, FIFO 순서

**기존 방식 (분산 락)**

```
평균 응답 시간: 5~10초
최대 응답 시간: 15초
성공: 100명
실패: 900명 (락 타임아웃)
순서 보장: ❌ (락 획득 순서 ≠ 요청 순서)
사용자 경험: 🔴 매우 나쁨
```

**개선 방식 (Redis BRPOP)**

```
평균 응답 시간: 50ms 이하
최대 응답 시간: 100ms
큐 추가 성공: 1000명 (중복 제외)
실제 발급: 100명 (정확)
Worker 처리 시간: 약 33초 (3개 스레드)
순서 보장: ✅ FIFO
사용자 경험: 🟢 우수
```

**성능 개선**
- **응답 속도: 99% 개선** (5~10초 → 50ms)
- **사용자 대기 시간: 완전 제거**
- **재고 정확성: 100% 보장**
- **처리 스레드: 99.7% 감소** (1000개 → 3개)
- **순서 보장: FIFO 추가**

**동시성 테스트 결과**

```
=== BlockingQueueCouponTest (5개 테스트) ===
✅ 10명 동시 요청 → 10개 발급 (정확)
✅ 20명 요청, 10개 재고 → 10개만 발급 (정확)
✅ 중복 요청 5회 → 1개만 발급 (중복 방지)
✅ FIFO 순서 보장 → 시간순 발급 확인
✅ 응답 속도 → 3초 이내 (10명 기준)

=== CouponQueueConcurrencyTest (3개 테스트) ===
✅ 1000명 요청, 100개 재고 → 100개만 발급
✅ 중복 요청 10회 → 1개만 발급
✅ 성능 비교 → 큐 방식이 5초 이내 응답
```

**재고 정확성 검증**

```
쿠폰 재고: 100/100 (발급/전체)
실제 UserCoupon 레코드: 100건
Redis Set 크기: 100명
✅ 데이터 일관성 완벽 유지
```

---

### 3.3 종합 성능 비교

| 기능 | 기존 방식 | 개선 방식 | 개선율 | 핵심 기술 |
|------|----------|----------|--------|----------|
| **인기 상품 조회** | 17,520ms | 697ms | **96%** | Redis Sorted Set |
| **쿠폰 발급 응답** | 5~10초 | 50ms | **99%** | Redis BRPOP Queue |
| **DB 부하** | 매우 높음 | 낮음 | **95%** | 집계 제거 + PK 조회 |
| **처리 스레드** | 1000개 | 3개 | **99.7%** | Worker Pool |
| **순서 보장** | ❌ | ✅ | - | FIFO Queue |

**비즈니스 임팩트**

1. **사용자 경험 개선**
   - 인기 상품 페이지: 17.5초 → 0.7초
   - 쿠폰 발급: 즉시 응답 (대기 시간 제거)
   - 실시간 랭킹 반영
   - 선착순 공정성 보장

2. **인프라 비용 절감**
   - DB 부하 95% 감소
   - 동시 처리량 증가
   - DB 커넥션 여유 확보

3. **확장성 확보**
   - Redis 기반 분산 아키텍처
   - 멀티 서버 환경 대응 가능

---

## 4. 결론

### 달성한 목표

✅ **성능 최적화**
- 인기 상품 조회: 96% 개선 (17.5초 → 0.7초)
- 쿠폰 발급 응답: 99% 개선 (5~10초 → 50ms)
- DB 부하: 95% 감소

✅ **실시간성**
- 주문 즉시 랭킹 반영
- 사용자가 항상 최신 인기 상품 확인
- 캐시 지연 없음

✅ **동시성 제어 + 순서 보장**
- 선착순 쿠폰 재고 정확성 100% 보장
- FIFO 순서 보장 추가
- 중복 발급 완벽 차단

✅ **확장성**
- Redis 기반 공유 자료구조
- 멀티 서버 환경 지원
- 수평 확장 가능한 아키텍처

---

### 핵심 인사이트

**1. 기술 선택 과정의 중요성**

각 기술의 장단점을 명확히 이해하고 요구사항에 맞게 선택:

- **인기 상품**: 실시간성 우선 → Spring Cache(빠름)보다 Redis Sorted Set(실시간) 선택
- **쿠폰 발급**: 순서 보장 + 안정성 → Pub/Sub(유실 위험)보다 BRPOP(안정성) 선택

**2. Trade-off의 이해**

완벽한 기술은 없으며, 상황에 맞는 선택이 중요:

- Redis 랭킹: 실시간성 ↔ 캐시 속도 (697ms vs 6.5ms)
- Redis BRPOP: 응답 속도 ↔ 발급 완료 시점
- 비즈니스 요구사항에 맞는 균형점 선택

**3. 비동기 처리의 위력**

사용자 응답과 실제 처리 분리:
- 응답 속도 99% 개선
- 자원 효율성 극대화
- 사용자 경험 대폭 향상

**4. Redis 자료구조 활용**

문제에 맞는 자료구조 선택:
- 실시간 랭킹 → Sorted Set (O(log N))
- 비동기 큐 → List + BRPOP (Blocking)
- 중복 방지 → Set (O(1))

---

### 향후 개선 방향

**단기 개선 (1개월)**
- [ ] 상품 정보 캐싱 추가 (697ms → 170ms 목표)
- [ ] WebSocket/SSE로 쿠폰 발급 완료 알림
- [ ] Redis 재고 캐싱으로 사전 재고 확인

**중기 개선 (3개월)**
- [ ] Redis Cluster로 고가용성 확보
- [ ] Worker 스레드 동적 조정 (부하에 따라)
- [ ] 큐 크기 모니터링 및 알람

**장기 개선 (6개월)**
- [ ] Redis Sentinel로 자동 Failover
- [ ] CQRS 패턴 적용 (읽기/쓰기 분리)

---

**기술 스택:** Java 17, Spring Boot 3.5.7, MySQL 8.0, Redis 7, Redisson
**테스트:** JUnit 5, Testcontainers, 8개 동시성 테스트 통과
**문서:** PERFORMANCE_COMPARISON.md, REDIS_SERIALIZATION_ISSUE.md
