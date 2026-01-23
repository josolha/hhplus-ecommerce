# E-commerce Core System

이커머스 핵심 기능 구현 프로젝트 - 동시성 제어부터 대규모 트래픽 처리까지

[![Java](https://img.shields.io/badge/Java-17-007396?logo=java)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-6DB33F?logo=spring)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.2-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Kafka-Latest-231F20?logo=apache-kafka)](https://kafka.apache.org/)

---

## 프로젝트 개요

**선착순 이벤트**, **재고 관리**, **대규모 트래픽**을 고려한 이커머스 백엔드 시스템 설계 및 구현

### 핵심 목표
- **동시성 제어**: 선착순 쿠폰 발급, 재고 차감, 잔액 관리
- **성능 최적화**: DB 쿼리 최적화, Redis 캐싱, 부하 테스트
- **비동기 처리**: Kafka 메시징, 이벤트 기반 아키텍처
- **안정성 확보**: Outbox 패턴, 트랜잭션 분리, 재시도 로직

---

## 주요 기능

- **상품 관리**: 재고 추적, 실시간 인기 상품 랭킹 (Redis Sorted Set)
- **주문/결제**: 장바구니, 잔액 결제, 쿠폰 할인, 재고 차감
- **선착순 쿠폰 발급**: Redis 분산락 + Kafka 큐 처리
- **외부 데이터 연동**: Outbox 패턴으로 안정적인 이벤트 전송

---

## 📚 목차

- [프로젝트 개요](#프로젝트-개요)
- [주요 기능](#주요-기능)
- [단계별 변화 과정](#단계별-변화-과정)
  - [V1: 요구사항 정의 및 설계](#v1-요구사항-정의-및-설계)
  - [V2: 레이어드 아키텍처 & 동시성 제어](#v2-레이어드-아키텍처--동시성-제어)
  - [V3: JPA 도입 & DB 쿼리 최적화](#v3-jpa-도입--db-쿼리-최적화)
  - [V4: 비관적 락 & 낙관적 락 적용](#v4-비관적-락--낙관적-락-적용)
  - [V5: Redis 분산락 & 트랜잭션 분리](#v5-redis-분산락--트랜잭션-분리)
  - [V6: Redis Sorted Set 랭킹 & 큐 시스템](#v6-redis-sorted-set-랭킹--큐-시스템)
  - [V7: 이벤트 기반 아키텍처](#v7-이벤트-기반-아키텍처)
  - [V8: Kafka 메시징 & Outbox Pattern](#v8-kafka-메시징--outbox-pattern)
  - [V9: k6 부하 테스트 & 성능 최적화](#v9-k6-부하-테스트--성능-최적화)
- [핵심 성과 요약](#핵심-성과-요약)
- [기술 스택](#기술-스택)
- [문서](#문서)
- [주요 학습 내용](#주요-학습-내용)
- [기술 블로그](#기술-블로그)

---

## 단계별 변화 과정

각 브랜치에서 **실제로 구현한 기능**과 **성능 개선 결과**를 확인할 수 있습니다.

<details>
<summary><strong>V1: 요구사항 정의 및 설계</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v1-requirements-definition)**

**설계 문서 작성**

- 요구사항 분석 (상품, 주문, 쿠폰, 외부 연동)
- **API 설계 명세** 작성 (20+ endpoints)
- **ERD 설계** (9개 테이블, Outbox 패턴 포함)
- **시퀀스 다이어그램** 작성 (주문, 쿠폰, 예외 처리 시나리오)

**산출물**: [API 설계](docs/api/API_DESIGN.md) | [ERD](docs/erd/ERD.dbml) | [시퀀스 다이어그램](docs/sequence/SEQUENCE_DIAGRAM.md)

</details>

<details>
<summary><strong>V2: 레이어드 아키텍처 & 동시성 제어</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v2-layered-architecture)**

**도메인 모델 구현 + ReentrantLock 동시성 제어**

**구현 기능**
- 도메인 엔티티 설계 (Product, Order, Coupon, User 등)
- UseCase 레이어 구현 (비즈니스 로직 분리)
- In-Memory Repository 구현

**도입 기술: ReentrantLock + ReadWriteLock**
```java
// 쿠폰별 개별 락 관리
private final ConcurrentHashMap<String, ReentrantLock> couponLocks;

// Repository 읽기/쓰기 성능 최적화
private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
```

**성과**
- 100명 동시 쿠폰 발급 테스트 통과 (중복 발급 0건)
- Lock 타임아웃 설정으로 데드락 방지

</details>

<details>
<summary><strong>V3: JPA 도입 & DB 쿼리 최적화</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v3-db-optimization)**

**JPA 전환 + 인덱스 최적화로 71% 성능 개선**

**구현 기능**
- In-Memory → JPA 엔티티 전환
- Testcontainers 기반 통합 테스트 환경 구축
- 대량 테스트 데이터 생성기 (LoadTestDataSeeder)

**도입 기술: Covering Index**
```sql
-- 인기 상품 조회 쿼리 최적화 (4단계 실험)
CREATE INDEX idx_orders_status_created_at
ON orders(status, created_at);

CREATE INDEX idx_order_items_covering
ON order_items(order_id, product_id, quantity);
```

**성과**
- **인덱스 없음**: 2,930ms
- **orders 인덱스**: 1,785ms (39% 감소)
- **Covering Index**: **840ms (71.3% 감소)**
- Left-most prefix rule 적용으로 중복 인덱스 제거 (8개 → 6개)

</details>

<details>
<summary><strong>V4: 비관적 락 & 낙관적 락 적용</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v4-concurrency-control)**

**도메인별 최적 락 전략 적용**

**도입 기술: 비관적 락 (Pessimistic Lock)**
```java
// 재고 차감 - SELECT FOR UPDATE
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :productId")
Product findByIdWithLock(@Param("productId") String productId);
```

**도입 기술: 낙관적 락 (Optimistic Lock)**
```java
// 잔액 차감 - @Version
@Entity
public class User {
    @Version
    private Long version;
    private Balance balance;
}
```

**도입 기술: 직접 UPDATE 쿼리**
```java
// 쿠폰 발급 - 원자적 수량 차감
@Modifying
@Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1
        WHERE c.id = :couponId AND c.issuedQuantity < c.maxQuantity")
int incrementIssuedQuantity(@Param("couponId") String couponId);
```

**추가 구현**
- Facade 패턴으로 주문 로직 리팩토링
- AOP 기반 로그 추적기 (ThreadLocal 활용)

**성과**
- 쿠폰: 비관적 락 + 직접 UPDATE (중복 발급 0건)
- 재고: SELECT FOR UPDATE (재고 부족 정확히 감지)
- 잔액: @Version으로 충돌 감지 후 재시도

</details>

<details>
<summary><strong>V5: Redis 분산락 & 트랜잭션 분리</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v5-redis-distributed-lock)**

**Redisson 분산락 + AOP 패턴 적용**

**도입 기술: Redisson 분산락**
```java
@DistributedLock(key = "#couponId")
public void issueCouponWithLock(String couponId, String userId) {
    // 쿠폰 발급 로직
}
```

**핵심 해결: Self-Invocation 문제**
```java
// AS-IS: @Transactional 작동 안 함
public void issueCoupon() {
    try (RLock lock = redissonClient.getLock("coupon")) {
        lock.lock();
        this.processCouponIssue(); // Self-invocation 문제
    }
}

// TO-BE: Service 레이어 분리
public class IssueCouponUseCase {
    private final CouponIssueService service;

    public void issueCoupon() {
        try (RLock lock = redissonClient.getLock("coupon")) {
            lock.lock();
            service.processCouponIssue(); // 프록시 정상 작동
        }
    }
}
```

**추가 구현**
- AOP 기반 분산락 (Spring EL 파라미터 지원)
- Redis 캐시 적용 (조회 API 성능 개선)
- Testcontainers (MySQL + Redis 통합 테스트)

**성과**
- DB 락 → 분산락 전환으로 서버 확장 대비
- @Transactional 정상 작동 (트랜잭션 분리 성공)

</details>

<details>
<summary><strong>V6: Redis Sorted Set 랭킹 & 큐 시스템</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v6-redis-ranking-and-async)**

**DB 집계 → Redis 전환으로 96% 성능 개선**

**도입 기술: Redis Sorted Set 실시간 랭킹**
```java
// 주문 완료 시 자동 랭킹 업데이트
public void updateProductRanking(String productId, int quantity) {
    redisTemplate.opsForZSet()
        .incrementScore("ranking:products", productId, quantity);
}

// 인기 상품 조회 (O(log N))
public List<Product> getTopProducts(int limit) {
    Set<String> productIds = redisTemplate.opsForZSet()
        .reverseRange("ranking:products", 0, limit - 1);
    return productRepository.findAllById(productIds);
}
```

**성과**
- **DB 집계**: 17,520ms (17.5초)
- **Redis Sorted Set**: **697ms (25배 빠름, 96% 감소)**
- 실시간 랭킹 업데이트 (~1ms 비동기 처리)

**추가 구현**
- Redis 큐 기반 선착순 쿠폰 발급
- StringRedisTemplate 직렬화 이슈 해결

</details>

<details>
<summary><strong>V7: 이벤트 기반 아키텍처</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v7-event-driven-order)**

**트랜잭션 분리로 외부 전송 실패에도 주문 성공 보장**

**도입 기술: @TransactionalEventListener + @Async**
```java
// 주문 완료 이벤트 발행
@Service
public class CreateOrderService {
    public Order createOrder(...) {
        Order order = orderFacade.processOrder(...);
        eventPublisher.publishEvent(new OrderCompletedEvent(order));
        return order; // 즉시 응답 (외부 전송 대기 X)
    }
}

// 비동기 이벤트 처리
@Component
public class OrderEventListener {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        externalService.send(event.getOrder());
    }
}
```

**핵심 해결**
- AFTER_COMMIT 타이밍 이슈: Order 엔티티 직접 전달 (DB 재조회 불필요)
- 트랜잭션 분리: 외부 전송 실패해도 주문 롤백 안 됨

**성과**
- 외부 API 2초 딜레이 + 10% 실패율에도 주문 정상 처리
- 사용자 응답 속도 향상 (동기 → 비동기)

</details>

<details>
<summary><strong>V8: Kafka 메시징 & Outbox Pattern</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v8-kafka)**

**Kafka + Outbox Pattern으로 메시지 유실 방지**

**도입 기술: Outbox Pattern**
```java
// 주문 완료 시 Outbox 테이블에 저장
@Transactional
public Order createOrder(...) {
    Order order = orderRepository.save(order);

    // Kafka 직접 발행 X, Outbox에 저장 O
    outboxEventRepository.save(OutboxEvent.create(
        "ORDER_COMPLETED",
        order.toJson()
    ));
    return order;
}

// 스케줄러가 주기적으로 전송 (5초마다)
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxEventRepository
        .findByStatusOrderByCreatedAtAsc(EventStatus.PENDING);

    for (OutboxEvent event : events) {
        kafkaProducer.send(event.getPayload());
        event.markAsPublished();
    }
}
```

**Exponential Backoff 재시도**
```java
public void scheduleRetry(OutboxEvent event) {
    int attempt = event.getRetryCount();
    long delaySeconds = (long) Math.pow(2, attempt) * 5; // 5초, 10초, 20초...
    event.scheduleNextRetry(delaySeconds);
}
```

**추가 최적화**
- UUID → BIGINT 전환 (내부 테이블 성능 개선)
- 쿠폰 발급도 Kafka 기반으로 전환

**성과**
- 주문 트랜잭션 커밋 후 Kafka 전송 (원자성 보장)
- 전송 실패 시 자동 재시도 (최대 3회)
- 메시지 유실 0건

</details>

<details>
<summary><strong>V9: k6 부하 테스트 & 성능 최적화</strong></summary>

**🔗 [브랜치 바로가기](https://github.com/josolha/hhplus-ecommerce/tree/feature/v9-load-test)**

**TPS 10.8배 향상 (7.54 → 81.68)**

**테스트 환경**
- 도구: k6 (ramping-vus 시나리오)
- 데이터: 100,000명 사용자, 10,000개 상품
- 부하: 100 VU (Virtual Users)

**핵심 최적화 #1: 전역 락 → 사용자별 락**
```java
// AS-IS: 모든 사용자가 동일한 락 대기
@DistributedLock(key = "'LOCK:order:global'")
public void processOrder() { ... }

// TO-BE: 사용자별 독립적인 락
@DistributedLock(key = "'LOCK:order:user:' + #userId")
public void processOrder(String userId) { ... }
```
**결과**: TPS 7.54 → 81.68 **(10.8배 향상)**

**핵심 최적화 #2: cart_id 인덱스 추가**
```sql
-- AS-IS: Full Table Scan (60ms)
SELECT * FROM cart_items WHERE cart_id = ?;

-- TO-BE: Index Scan (2ms)
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
```
**결과**: 60ms → 2ms **(30배 개선)**

**핵심 최적화 #3: HikariCP Connection Pool 확대**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 100  # 10 → 100
```
**결과**: p(95) 404ms → 131ms **(67% 개선)**

**최종 성능 지표**

| API | TPS | p(95) 응답시간 | 동시성 제어 |
|-----|-----|---------------|------------|
| 쿠폰 발급 | 372 req/s | 97ms | 중복 발급 0건 |
| 잔액 충전 | 551 req/s | 288ms | 충돌 0건 |
| 주문/결제 | **82 req/s** | **131ms** | 재고 정합성 보장 |

**문서**
- [부하 테스트 종합 보고서](docs/loadtest/LOAD_TEST_TOTAL.md)
- [장애 대응 보고서](docs/loadtest/INCIDENT_REPORT.md)

</details>

---

## 핵심 성과 요약

### 성능 개선
- **DB 쿼리 최적화**: 2,930ms → 840ms (71.3% 감소)
- **Redis 랭킹 전환**: 17.5초 → 697ms (96% 감소, 25배)
- **주문 API 최적화**: TPS 7.54 → 81.68 (10.8배 향상)
- **장바구니 인덱스**: 60ms → 2ms (30배 개선)

### 동시성 제어
- ReentrantLock → Redis 분산락 전환 (서버 확장 대비)
- 도메인별 최적 락 전략 (비관적/낙관적/직접 UPDATE)
- 100명 동시 쿠폰 발급 테스트 통과 (중복 0건)

### 안정성 확보
- Outbox Pattern으로 메시지 유실 방지
- 외부 API 장애에도 주문 정상 처리
- Exponential Backoff 재시도 로직

### 아키텍처 개선
- 레이어드 아키텍처 → 이벤트 기반 아키텍처
- 동기 처리 → 비동기 처리 (Kafka, @Async)
- Self-Invocation 문제 해결 (트랜잭션 분리)

---

## 기술 스택

### Backend
- **Framework**: Spring Boot 3.5.7
- **Language**: Java 17
- **Database**: MySQL 8.0
- **Cache**: Redis 7.2, Redisson
- **Messaging**: Kafka

### Test & Monitoring
- **Test**: JUnit 5, Testcontainers, k6
- **Load Test**: k6 (ramping-vus)
- **Monitoring**: Prometheus, Grafana

---

## 문서

- [API 설계 명세](docs/api/API_DESIGN.md)
- [ERD 설계](docs/erd/ERD.dbml)
- [시퀀스 다이어그램](docs/sequence/SEQUENCE_DIAGRAM.md)
- [부하 테스트 보고서](docs/loadtest/LOAD_TEST_TOTAL.md)
- [장애 대응 보고서](docs/loadtest/INCIDENT_REPORT.md)
- [JPA 트러블슈팅](docs/troubleshooting/JPA_DIRTY_CHECKING_ISSUE.md)

---

## 주요 학습 내용

### 동시성 제어
- DB 락 (비관적/낙관적) vs Redis 분산락 trade-off
- AOP 기반 분산락 패턴
- Self-Invocation 문제 해결

### 성능 최적화
- Covering Index 설계
- Redis 자료구조 활용 (Sorted Set)
- Connection Pool 튜닝

### 아키텍처
- 이벤트 기반 아키텍처 설계
- Outbox Pattern 구현
- 트랜잭션 경계 설정

---

## 기술 블로그

프로젝트 구현 과정에서 학습한 내용과 트러블슈팅을 정리했습니다.

- [JDK vs CGLIB, Self-Invocation 완벽 정리](https://josolha.tistory.com/78)
- [UserCoupon Dirty Checking 실패 이슈 해결 과정](https://josolha.tistory.com/77)
- [@TransactionEventListener 트러블 슈팅, 문제점](https://josolha.tistory.com/76)
- [Redis 직렬화 문제 해결과정](https://josolha.tistory.com/75)
- [Redisson 분산 락 AOP로 리팩토링하기](https://josolha.tistory.com/74)
- [Spring @Transactional이 동작하지 않는 경우: Self-Invocation 문제와 해결](https://josolha.tistory.com/73)
- [Redis 분산락](https://josolha.tistory.com/72)
- [동시성(Concurrency) 문제](https://josolha.tistory.com/71)
- [Testcontainers 통합 테스트에서 발생한 JPA ID 자동 생성 문제](https://josolha.tistory.com/69)
- [TDD로 포인트 관리 시스템 구현](https://josolha.tistory.com/67)


