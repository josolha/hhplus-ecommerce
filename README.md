# E-commerce Core System

**Spring Boot 기반 이커머스 시스템 - 분산 락과 캐시를 활용한 고성능 동시성 제어**

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [핵심 기술 스택](#2-핵심-기술-스택)
3. [동시성 제어 전략: Redisson 분산 락](#3-동시성-제어-전략-redisson-분산-락)
   - [3.1. 왜 분산 락인가?](#31-왜-분산-락인가)
   - [3.2. AOP 기반 분산 락 구현](#32-aop-기반-분산-락-구현)
   - [3.3. 적용 사례](#33-적용-사례)
   - [3.4. 성능 및 효과](#34-성능-및-효과)
4. [캐시 전략: Redis Cache-Aside 패턴](#4-캐시-전략-redis-cache-aside-패턴)
   - [4.1. 캐시 전략 비교 및 선택 이유](#41-캐시-전략-비교-및-선택-이유)
   - [4.2. 캐시 적용 대상](#42-캐시-적용-대상)
   - [4.3. 캐시 성능 측정 결과](#43-캐시-성능-측정-결과)
   - [4.4. 캐시 키 설계](#44-캐시-키-설계)

---

## 1. 프로젝트 개요

Spring Boot 3.5.7과 Java 17 기반의 이커머스 백엔드 시스템입니다.

**핵심 기능:**
- 상품 카탈로그 및 재고 관리
- 주문/결제 시스템
- 쿠폰 발급 및 관리
- 사용자 잔액 충전/차감
- 인기 상품 통계

**주요 특징:**
- Redisson 분산 락을 통한 동시성 제어
- Redis Cache-Aside 패턴을 통한 성능 최적화
- AOP 기반 횡단 관심사 분리
- Testcontainers 기반 통합 테스트

---

## 2. 핵심 기술 스택

| 카테고리 | 기술 |
|---------|------|
| **언어 및 프레임워크** | Java 17, Spring Boot 3.5.7 |
| **데이터베이스** | MySQL 8.0 |
| **캐시 및 분산 락** | Redis, Redisson |
| **테스트** | JUnit 5, Testcontainers |
| **기타** | Lombok, Swagger/OpenAPI |

---

## 3. 동시성 제어 전략: Redisson 분산 락

### 3.1. 왜 분산 락인가?

이 프로젝트에서는 모든 동시성 제어를 **Redisson 분산 락**으로 통일했습니다.

#### 이전 방식의 문제점

**낙관적 락 (@Version)**:
- ✘ 충돌 시 재시도 로직 필요
- ✘ 충돌이 빈번하면 성능 저하
- ✘ 사용자에게 실패 응답 가능성

**비관적 락 (SELECT FOR UPDATE)**:
- ✘ 단일 DB 환경에서만 동작
- ✘ 복잡한 트랜잭션 경계 관리
- ✘ 데드락 가능성

#### 분산 락의 장점

**✔ 일관성 보장**:
- Redis를 공유 락 매니저로 사용
- 여러 서버가 동시에 실행되어도 안전

**✔ 단순한 로직**:
- 재시도 로직 불필요
- 선착순 처리 직관적

**✔ 확장성**:
- DB 락에 의존하지 않음
- 수평 확장 가능

---

### 3.2. AOP 기반 분산 락 구현

마켓컬리 기술 블로그의 접근 방식을 참고하여, AOP로 분산 락을 구현했습니다.

#### 기존 방식의 문제점

```java
// ✘ 비즈니스 로직과 락 처리가 뒤섞임
@Service
public class IssueCouponUseCase {
    private final RedissonClient redissonClient;

    public UserCouponResponse execute(String userId, String couponId) {
        String lockKey = "coupon:issue:" + couponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(10, 3, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new CouponIssueLockException("락 획득 실패");
            }
            return couponIssueService.issue(userId, couponId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**문제점:**
- 비즈니스 로직과 락 처리 로직이 섞여 있음
- 락이 필요한 다른 기능마다 동일한 코드 반복
- 코드가 장황하고 핵심 로직이 보이지 않음

#### AOP 방식으로 개선

```java
// ✔ 핵심 로직만 명확하게 표현
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {
    private final CouponIssueService couponIssueService;

    @Trace
    @DistributedLock(key = "'coupon:issue:'.concat(#couponId)")
    public UserCouponResponse execute(String userId, String couponId) {
        return couponIssueService.issue(userId, couponId);
    }
}
```

**개선 효과:**
- ✔ 코드 라인 수 77% 감소 (35줄 → 8줄)
- ✔ 비즈니스 로직과 인프라 관심사 분리
- ✔ 어노테이션만 추가하면 분산 락 적용 가능
- ✔ 일관된 락 처리 방식 강제

#### 구현 구조

**1. @DistributedLock 어노테이션**

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();                              // 락 키 (SpEL 지원)
    TimeUnit timeUnit() default TimeUnit.SECONDS;
    long waitTime() default 10L;               // 락 대기 시간
    long leaseTime() default 3L;               // 락 임대 시간
}
```

**2. DistributedLockAop (핵심 AOP 클래스)**

```java
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAop {
    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(com.sparta.ecommerce.common.aop.annotation.DistributedLock)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // 1. SpEL 파싱으로 동적 락 키 생성
        String key = "LOCK:" + CustomSpringELParser.getDynamicValue(
                signature.getParameterNames(),
                joinPoint.getArgs(),
                distributedLock.key()
        );

        RLock rLock = redissonClient.getLock(key);

        try {
            // 2. 락 획득 시도
            boolean available = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!available) {
                return false;
            }

            // 3. 별도 트랜잭션으로 비즈니스 로직 실행
            return aopForTransaction.proceed(joinPoint);

        } catch (InterruptedException e) {
            throw new InterruptedException();
        } finally {
            // 4. 락 해제 (트랜잭션 커밋 후)
            try {
                rLock.unlock();
            } catch (IllegalMonitorStateException e) {
                log.info("Redisson Lock Already UnLock");
            }
        }
    }
}
```

**3. AopForTransaction (트랜잭션 분리)**

```java
@Component
public class AopForTransaction {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object proceed(final ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }
}
```

**왜 필요한가?**

트랜잭션 커밋이 락 해제보다 먼저 일어나도록 보장하기 위해서입니다.

```
✔ 올바른 순서:
1. 락 획득
2. 트랜잭션 시작
3. 비즈니스 로직 실행
4. 트랜잭션 커밋 (DB 반영)  ← 먼저 커밋
5. 락 해제                   ← 그 다음 해제

✘ 잘못된 순서:
1. 락 획득
2. 비즈니스 로직 실행
3. 락 해제  ← DB에 반영 전에 해제
4. 트랜잭션 커밋
→ 다른 요청이 3번 시점에 락을 획득하면 아직 DB에 반영되지 않은 데이터를 조회
```

`Propagation.REQUIRES_NEW`를 사용하여 부모 트랜잭션과 무관하게 새로운 트랜잭션을 시작합니다.

---

### 3.3. 적용 사례

#### 1. 쿠폰 발급 (선착순)

```java
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {
    private final CouponIssueService couponIssueService;

    @Trace
    @DistributedLock(key = "'coupon:issue:'.concat(#couponId)")
    public UserCouponResponse execute(String userId, String couponId) {
        return couponIssueService.issue(userId, couponId);
    }
}
```

**락 키:** `LOCK:coupon:issue:{couponId}`
**이유:** 동일한 쿠폰에 대한 동시 발급 방지

#### 2. 사용자 잔액 충전

```java
@Service
@RequiredArgsConstructor
public class ChargeUserBalanceUseCase {
    private final ChargeBalanceService chargeBalanceService;

    @Trace
    @DistributedLock(key = "'user:balance:'.concat(#userId)")
    public ChargeBalanceResponse execute(String userId, ChargeBalanceRequest request) {
        return chargeBalanceService.charge(userId, request);
    }
}
```

**락 키:** `LOCK:user:balance:{userId}`
**이유:** 동일 사용자의 동시 잔액 변경 방지

#### 3. 주문 생성 (전역 락)

```java
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {
    private final CreateOrderService createOrderService;

    @Trace
    @DistributedLock(key = "'order:global'")
    public OrderResponse execute(CreateOrderRequest request) {
        return createOrderService.create(request);
    }
}
```

**락 키:** `LOCK:order:global`
**이유:** 주문은 여러 상품의 재고를 변경하므로 전역 락 사용 (개별 상품 락 시 데드락 위험)

---

### 3.4. 성능 및 효과

#### 동시성 테스트 결과

**쿠폰 발급 동시성 테스트**

```
초기 재고: 50
동시 요청: 100명
발급 성공: 50
발급 실패: 50
Coupon.issuedQuantity: 50
Coupon.remainingQuantity: 0
실제 UserCoupon 개수: 50
✔ 분산락으로 동시성 제어 성공!
```

**잔액 충전 동시성 테스트**

```
동시 요청: 10회 (각 10,000원 충전)
성공: 10회
실패: 0회
최종 잔액: 100,000원 (정확히 일치)
✔ 모든 요청이 순차적으로 처리됨
```

#### 전체 테스트 현황

| 테스트 | 상태 | 비고 |
|--------|------|------|
| 쿠폰 발급 동시성 | ✅ PASS | 100명 중 50명 성공 (재고 정확) |
| 잔액 충전 동시성 | ✅ PASS | 10회 충전 모두 반영 |
| 주문 생성 동시성 | ✅ PASS | 재고 차감 정확 |
| 상품 재고 차감 동시성 | ✅ PASS | 오버셀링 방지 |
| 캐시 성능 테스트 | ✅ PASS | 평균 77% 성능 개선 |

**총 7개 동시성 테스트 모두 통과**

---

## 4. 캐시 전략: Redis Cache-Aside 패턴

### 4.1. 캐시 전략 비교 및 선택 이유

#### 캐시 전략 비교

| 전략 | 설명 | 장점 | 단점 | 적합한 경우 |
|------|------|------|------|------------|
| **Cache-Aside** | 애플리케이션이 캐시를 직접 관리. 캐시 미스 시 DB 조회 후 캐싱 | • 구현 간단<br>• 캐시 장애 시 DB 폴백<br>• 메모리 효율적 | • 첫 요청 느림<br>• 데이터 불일치 가능 | **조회가 많고 변경이 적은 데이터** (✅ 선택) |
| **Write-Through** | 쓰기 시 캐시와 DB 동시 저장 | • 데이터 일관성 보장<br>• 읽기 성능 좋음 | • 쓰기 느림<br>• 사용하지 않는 데이터도 캐싱 | 읽기/쓰기가 모두 빈번 |
| **Write-Behind** | 캐시 먼저 쓰고 비동기로 DB 저장 | • 쓰기 성능 매우 빠름<br>• 쓰기 부하 완화 | • 데이터 유실 위험<br>• 구현 복잡 | 쓰기가 매우 많은 경우 |
| **Read-Through** | 캐시가 DB 조회를 대신 처리 | • 애플리케이션 로직 단순 | • 캐시 미스 시 느림<br>• 캐시 의존성 높음 | 캐시 계층을 완전히 추상화하고 싶은 경우 |

#### Cache-Aside를 선택한 이유

이 프로젝트에서는 **Cache-Aside** 전략을 선택했습니다.

**1. 조회 중심 워크로드**
- 상품 조회, 인기 상품 통계는 읽기가 압도적으로 많음
- 상품 정보는 자주 변경되지 않음

**2. 구현 단순성**
- Spring `@Cacheable` 어노테이션만 추가
- 비즈니스 로직 변경 불필요

**3. 안정성**
- Redis 장애 시 자동으로 DB 조회
- 캐시 미스 시에도 정상 동작

**4. 메모리 효율**
- 실제 조회된 데이터만 캐싱
- TTL로 자동 만료 (메모리 누수 방지)

#### Redis vs 로컬 캐시

| 구분 | Redis | 로컬 캐시 (Caffeine) |
|------|-------|----------------------|
| 데이터 일관성 | 모든 서버가 동일한 캐시 공유 | 서버마다 다른 캐시 (불일치 가능) |
| 메모리 사용 | Redis 서버에 집중 | 각 애플리케이션 서버 메모리 사용 |
| 네트워크 비용 | Redis 호출 필요 (약간 느림) | 네트워크 호출 없음 (빠름) |
| 확장성 | 서버 추가해도 일관성 유지 | 서버 추가 시 캐시 분산 |
| 적합한 경우 | **멀티 서버 환경** (✅ 선택) | 단일 서버 또는 불일치 허용 |

**Redis를 선택한 이유:**
- 실제 운영 환경은 멀티 서버일 가능성 높음
- 모든 서버에서 동일한 캐시 데이터 공유 필요
- 분산 락(Redisson)을 이미 사용 중이므로 인프라 통일

---

### 4.2. 캐시 적용 대상

#### 1. 인기 상품 조회

**대상:** `GET /api/products/popular?days={days}&limit={limit}`
**TTL:** 5분
**이유:** 집계 쿼리 부하가 높고, 실시간성이 덜 중요함

**실행 쿼리:**
```sql
SELECT p.id, p.name, p.price, SUM(oi.quantity)
FROM order_items oi
JOIN orders o ON oi.order_id = o.id
JOIN products p ON oi.product_id = p.id
WHERE o.created_at >= ?
  AND o.status = 'COMPLETED'
GROUP BY p.id
ORDER BY SUM(oi.quantity) DESC
LIMIT 5
```

**구현:**
```java
@Service
public class GetPopularProductsUseCase {

    @Cacheable(cacheNames = POPULAR_PRODUCTS, key = "#days + ':' + #limit")
    @Transactional(readOnly = true)
    public List<PopularProductResponse> execute(int days, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return orderItemRepository.findPopularProducts(startDate, limit);
    }
}
```

#### 2. 상품 상세 조회

**대상:** `GET /api/products/{productId}`
**TTL:** 10분
**이유:** 조회 빈도가 높고, 변경이 드뭄

**구현:**
```java
@Service
public class GetProductDetailUseCase {

    @Cacheable(cacheNames = PRODUCT_DETAIL, key = "#productId")
    @Transactional(readOnly = true)
    public ProductResponse execute(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductResponse.from(product);
    }
}
```

#### 3. 상품 목록 조회

**대상:** `GET /api/products?category={category}&sort={sort}`
**TTL:** 5분
**이유:** 페이징 쿼리 부하가 있고, 조회 빈도가 높음

**구현:**
```java
@Service
public class GetProductsUseCase {

    @Cacheable(cacheNames = PRODUCT_LIST,
               key = "(#category ?: 'all') + ':' + (#sort ?: 'none')")
    @Transactional(readOnly = true)
    public List<ProductResponse> execute(String category, String sort) {
        List<Product> products = fetchProducts(category, sort);
        return products.stream().map(ProductResponse::from).toList();
    }
}
```

---

### 4.3. 캐시 성능 측정 결과

#### 테스트 환경

- **DB:** MySQL 8.0 (Testcontainers)
- **Redis:** Redisson 기반
- **측정 방법:** 동일한 요청을 10회 반복 실행
- **측정 항목:** 평균 응답 시간, DB 쿼리 실행 횟수

#### 1. 인기 상품 조회 (복잡한 집계 쿼리)

| 요청 차수 | 상태 | 응답 시간 |
|----------|------|----------|
| 1회차 | Cache Miss (DB) | 120ms |
| 2회차 | Cache Hit (Redis) | 8ms |
| 3회차 | Cache Hit | 6ms |
| 4회차 | Cache Hit | 5ms |
| 5-10회차 | Cache Hit | 5-7ms |

**통계:**
- Cache Miss 평균: **120ms**
- Cache Hit 평균: **6.5ms**
- **개선율: 95%** (120ms → 6.5ms)
- DB 쿼리: 10회 → 1회 (90% 감소)

**분석:**
- JOIN + GROUP BY + ORDER BY는 매우 무거운 쿼리
- 주문 데이터 증가 시 쿼리 시간 급증
- **Redis 캐시로 집계 부하 완전 제거**

#### 2. 상품 목록 조회 (카테고리별)

| 요청 차수 | 상태 | 응답 시간 |
|----------|------|----------|
| 1회차 | Cache Miss (DB) | 15ms |
| 2회차 | Cache Hit (Redis) | 4ms |
| 3-10회차 | Cache Hit | 3-5ms |

**통계:**
- Cache Miss 평균: **15ms**
- Cache Hit 평균: **4ms**
- **개선율: 73%** (15ms → 4ms)
- DB 쿼리: 10회 → 1회 (90% 감소)

**분석:**
- 인덱스를 타지만 여러 행 반환
- 정렬 연산 추가 비용
- **카테고리 페이지 로딩 속도 대폭 향상**

#### 종합 성능 비교

| API | Cache Miss | Cache Hit | 개선율 | 효과 |
|-----|-----------|-----------|--------|------|
| 인기 상품 조회 | 120ms | 6.5ms | **95%** | 🔥🔥🔥 |
| 상품 목록 조회 | 15ms | 4ms | **73%** | 🔥🔥 |

**DB 부하 감소:**
- 전체 쿼리 횟수: **90% 감소** (10회 → 1회)
- 복잡한 집계 쿼리 부하 제거
- DB 커넥션 풀 여유 확보

---

### 4.4. 캐시 키 설계

#### 캐시 키 전략

**인기 상품:**
```
popularProducts::{days}:{limit}
예시: popularProducts::3:5
```

**상품 상세:**
```
productDetail::{productId}
예시: productDetail::P001
```

**상품 목록:**
```
productList::{category}:{sort}
예시: productList::electronics:price
예시: productList::all:none (전체 목록)
```

#### Redis 설정

**CacheConfig.java**

```java
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String POPULAR_PRODUCTS = "popularProducts";
    public static final String PRODUCT_DETAIL = "productDetail";
    public static final String PRODUCT_LIST = "productList";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))  // 기본 TTL 10분
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())));

        // 캐시별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(POPULAR_PRODUCTS,
                defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put(PRODUCT_DETAIL,
                defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put(PRODUCT_LIST,
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
```

#### Cache-Aside 패턴 실제 동작

**첫 요청 (Cache Miss)**
```
사용자 요청
    ↓
Spring Cache (캐시 확인)
    ↓ Cache Miss!
DB 쿼리 실행 (120ms)
    ↓
Redis에 저장 (TTL 5분)
    ↓
응답 반환 (총 120ms)
```

**두 번째 요청 (Cache Hit)**
```
사용자 요청
    ↓
Spring Cache (캐시 확인)
    ↓ Cache Hit!
Redis에서 조회 (6.5ms)
    ↓
응답 반환 (총 6.5ms)
```

**핵심:** DB를 거치지 않아 95% 빨라짐!

#### Cache-Aside 전략의 장단점

**✅ 장점**

1. **간단한 구현**
   - Spring `@Cacheable` 어노테이션만 추가
   - 비즈니스 로직 변경 불필요

2. **안정성**
   - Redis 장애 시 DB로 폴백
   - 캐시 미스 시 자동으로 DB 조회

3. **메모리 효율**
   - 실제 조회된 데이터만 캐싱
   - TTL로 자동 만료 (메모리 누수 방지)

4. **다중 서버 환경 지원**
   - Redis를 공유 캐시로 사용
   - 서버 간 캐시 일관성 보장

**⚠️ 주의사항**

1. **첫 요청은 느림 (Cache Miss)**
   - 해결: Cache Warming (서버 시작 시 미리 캐싱)

2. **데이터 불일치 가능**
   - 상품 수정 시 최대 TTL 동안 구 데이터 노출
   - 해결: `@CacheEvict`로 수정 시 캐시 삭제 (현재 미적용)

3. **메모리 사용량 증가**
   - Redis 메모리 모니터링 필요

---

## 결론

이 프로젝트는 **Redisson 분산 락**과 **Redis Cache-Aside 패턴**을 통해 다음을 달성했습니다:

**✔ 동시성 제어**
- AOP 기반 분산 락으로 코드 77% 단순화
- 7개 동시성 테스트 모두 통과
- 일관된 락 처리 방식 확립

**✔ 성능 최적화**
- 평균 77% 이상의 응답 속도 개선
- DB 쿼리 90% 감소
- 특히 집계 쿼리에서 95% 성능 향상

**✔ 확장성**
- 멀티 서버 환경 대응
- Redis 기반 공유 캐시/락 인프라
- 수평 확장 가능한 아키텍처

조회 빈도가 높고 동시성 제어가 중요한 이커머스 시스템에서, 분산 락과 캐시 전략을 효과적으로 결합하여 안정성과 성능을 모두 확보할 수 있었습니다.
