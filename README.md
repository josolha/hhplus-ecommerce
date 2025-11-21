# E-Commerce Core System

> Spring Boot 기반 이커머스 백엔드 시스템 - 동시성 제어 

---

## 📋 목차

- [동시성 제어 전략](#-동시성-제어-전략)
  - [전략 개요](#전략-개요)
  - [상품 재고 - 비관적 락](#1-상품-재고---비관적-락--직접-update-쿼리)
  - [쿠폰 발급 - 비관적 락](#2-쿠폰-발급---비관적-락--직접-update-쿼리)
  - [잔액 충전/차감 - 낙관적 락](#3-잔액-충전차감---낙관적-락-version)
  - [왜 이렇게 설계했는가?](#-왜-이렇게-설계했는가)
- [쿼리 최적화](#-e-commerce-인기-상품-조회-쿼리-최적화)

---

## 🔐 동시성 제어 전략

### 전략 개요

이커머스 시스템에서 동시성 문제가 발생할 수 있는 핵심 영역과 각 영역에 적용한 제어 전략입니다.

| 리소스 | 락 유형 | 경합 수준 | 선택 이유 |
|--------|---------|-----------|-----------|
| 상품 재고 | 비관적 락 + 직접 UPDATE | 높음 | 다수 사용자가 동일 상품 주문, 재고 정확성 필수 |
| 쿠폰 재고 | 비관적 락 + 직접 UPDATE | 매우 높음 | 선착순 발급, 초과 발급 방지 필수 |
| 사용자 잔액 | 낙관적 락 (@Version) | 낮음 | 개인 리소스, 충돌 확률 낮음 |

---

### 1. 상품 재고 - 비관적 락 + 직접 UPDATE 쿼리

#### 적용 코드

**ProductRepository.java**
```java
// 비관적 락으로 상품 조회
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.productId = :productId")
Optional<Product> findByIdWithLock(@Param("productId") String productId);

// 직접 UPDATE 쿼리로 재고 차감
@Modifying
@Query("UPDATE Product p SET p.stock.quantity = p.stock.quantity - :amount WHERE p.productId = :productId")
int decreaseStock(@Param("productId") String productId, @Param("amount") int amount);
```

**OrderFacade.java**
```java
private void deductStock(List<Product> lockedProducts, List<CartItem> cartItems) {
    for (int i = 0; i < cartItems.size(); i++) {
        CartItem cartItem = cartItems.get(i);
        Product product = lockedProducts.get(i);
        // 직접 UPDATE 쿼리 사용
        productRepository.decreaseStock(product.getProductId(), cartItem.getQuantity());
    }
}
```

#### 선택 이유

**1. 높은 경합 상황**
- 인기 상품은 여러 사용자가 동시에 주문
- 같은 상품 레코드에 대한 동시 접근 빈번
- Lost Update 발생 시 초과 판매(재고 음수) 위험

**2. 비관적 락이 적합한 이유**
```
낙관적 락 사용 시:
- 50명이 동시 주문 → 1명 성공, 49명 재시도
- 재시도 비용이 매우 높음 (전체 주문 프로세스 반복)
- 사용자 경험 저하

비관적 락 사용 시:
- SELECT FOR UPDATE로 행 락 획득
- 다른 트랜잭션은 락 획득까지 대기
- 모든 트랜잭션이 순차적으로 성공
- 재시도 없이 일관된 처리
```

**3. 직접 UPDATE 쿼리 사용 이유**
```
JPA save() 문제:
- @Embedded 객체(Stock)의 dirty checking 불안정
- 영속성 컨텍스트와 DB 불일치 발생 가능

직접 UPDATE 장점:
- DB 레벨에서 원자적 연산 보장
- JPA 영속성 컨텍스트 우회
- 100% 확실한 재고 차감
```

#### 동시성 테스트

```java
@Test
void createOrder_ConcurrentStock_Overselling() {
    // 100명이 재고 10개 상품 동시 주문
    // 예상: 10명 성공, 90명 실패
    // 결과: 정확히 10개만 판매됨
    assertThat(successCount.get()).isEqualTo(10);
    assertThat(finalStock).isEqualTo(0);
}
```

---

### 2. 쿠폰 발급 - 비관적 락 + 직접 UPDATE 쿼리

#### 적용 코드

**CouponRepository.java**
```java
// 비관적 락으로 쿠폰 조회
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Coupon c WHERE c.couponId = :couponId")
Optional<Coupon> findByIdWithLock(@Param("couponId") String couponId);

// 직접 UPDATE 쿼리로 재고 차감
@Modifying
@Query("UPDATE Coupon c SET c.stock.issuedQuantity = c.stock.issuedQuantity + 1, " +
       "c.stock.remainingQuantity = c.stock.remainingQuantity - 1 " +
       "WHERE c.couponId = :couponId")
int issueCoupon(@Param("couponId") String couponId);
```

**UserCouponRepository.java**
```java
// 중복 발급 체크도 락 적용
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT COUNT(uc) > 0 FROM UserCoupon uc WHERE uc.userId = :userId AND uc.couponId = :couponId")
boolean existsByUserIdAndCouponIdWithLock(@Param("userId") String userId, @Param("couponId") String couponId);
```

**IssueCouponUseCase.java**
```java
@Transactional
public UserCouponResponse execute(String userId, String couponId) {
    // 1. 비관적 락으로 쿠폰 조회
    Coupon coupon = couponRepository.findByIdWithLock(couponId)
            .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰입니다"));

    // 2. 중복 발급 체크 (락 보호)
    if (userCouponRepository.existsByUserIdAndCouponIdWithLock(userId, couponId)) {
        throw new DuplicateCouponIssueException(couponId);
    }

    // 3. 재고 확인 및 차감
    if (!coupon.hasStock()) {
        throw new CouponSoldOutException(couponId);
    }

    // 4. 직접 UPDATE 쿼리로 재고 차감
    couponRepository.issueCoupon(couponId);

    // 5. 사용자 쿠폰 발급 이력 저장
    UserCoupon userCoupon = UserCoupon.issue(userId, coupon);
    userCouponRepository.save(userCoupon);

    return UserCouponResponse.from(userCoupon, coupon);
}
```

#### 선택 이유

**1. 매우 높은 경합 상황**
- 선착순 쿠폰 발급은 이커머스에서 가장 높은 경합 시나리오
- 100명 이상이 동시에 같은 쿠폰 발급 시도
- 재고(예: 50개)보다 많은 요청이 순간적으로 집중

**2. 두 가지 동시성 문제**
```
문제 1: 초과 발급
- 재고 50개인데 100명이 동시 발급
- Race condition으로 50개 이상 발급 가능
- 해결: 비관적 락 + 직접 UPDATE

문제 2: 중복 발급
- 같은 사용자가 동시에 10번 발급 시도
- 중복 체크와 발급 사이에 Race condition
- 해결: 중복 체크에도 비관적 락 적용
```

**3. 낙관적 락이 부적합한 이유**
```
100명 동시 요청 시:
- 낙관적 락: 1명 성공, 99명 OptimisticLockException
- 99명 모두 재시도 필요
- 재시도 폭증으로 시스템 부하 급증
- 서버 장애 위험

비관적 락:
- 순차적으로 50명 성공
- 나머지 50명은 "재고 소진" 응답
- 불필요한 재시도 없음
- 안정적인 시스템 운영
```

#### 동시성 테스트

```java
@Test
void issueCoupon_ConcurrentIssue_Overselling() {
    // 100명이 재고 50개 쿠폰 동시 발급 시도
    assertThat(successCount.get()).isEqualTo(50);
    assertThat(failCount.get()).isEqualTo(50);
    assertThat(actualIssuedCount).isEqualTo(50);
}

@Test
void issueCoupon_SameUser_DuplicateIssue() {
    // 같은 사용자가 10번 동시 발급 시도
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(9);
    assertThat(userCouponCount).isEqualTo(1);
}
```

---

### 3. 잔액 충전/차감 - 낙관적 락 (@Version)

#### 적용 코드

**User.java**
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    private String userId;

    @Embedded
    private Balance balance;

    @Version  // 낙관적 락
    private Long version;

    public void chargeBalance(long amount) {
        this.balance = balance.charge(amount);
    }

    public void deductBalance(long amount) {
        this.balance = balance.deduct(amount);
    }
}
```

**ChargeUserBalanceUseCase.java**
```java
@Transactional
public ChargeBalanceResponse execute(String userId, ChargeBalanceRequest request) {
    // 일반 조회 (낙관적 락 사용)
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

    user.chargeBalance(request.amount());
    userRepository.save(user);  // @Version 체크

    return new ChargeBalanceResponse(user.getBalance().amount());
}
```

**PaymentService.java**
```java
private Payment processBalancePayment(Order order) {
    // 일반 조회 (낙관적 락 사용)
    User user = userRepository.findById(order.getUserId())
            .orElseThrow(() -> new PaymentFailedException("사용자를 찾을 수 없습니다"));

    // 잔액 확인 및 차감
    if (!user.getBalance().isSufficient(order.getFinalAmount())) {
        throw new InsufficientBalanceException("잔액 부족");
    }

    user.deductBalance(order.getFinalAmount());
    userRepository.save(user);  // @Version 체크로 동시성 제어

    // ...
}
```

#### 선택 이유

**1. 낮은 경합 상황**
- 잔액은 개인 리소스 (한 사용자의 잔액에 다른 사용자가 접근하지 않음)
- 동시 충전/차감은 같은 사용자가 여러 기기에서 접근할 때만 발생
- 현실적으로 충돌 확률이 매우 낮음

**2. 낙관적 락이 적합한 이유**
```
경합 상황 분석:
- 상품 재고: N명이 1개 상품 → 경합률 높음
- 쿠폰 재고: N명이 1개 쿠폰 → 경합률 매우 높음
- 사용자 잔액: 1명이 자신의 잔액 → 경합률 낮음

낙관적 락 장점:
- 락 대기 시간 없음 (비관적 락은 대기 필요)
- 처리량(throughput) 향상
- 데드락 가능성 없음
- 코드가 간단 (@Version만 추가)

충돌 시:
- OptimisticLockException 발생
- 재시도 로직으로 처리
- 충돌 확률이 낮으므로 재시도 비용 미미
```

**3. 비관적 락이 부적합한 이유**
```
잔액 작업에 비관적 락 사용 시:
- 모든 잔액 조회에서 SELECT FOR UPDATE
- 불필요한 DB 락 부하
- 처리량 감소
- 데드락 위험 (주문 시 여러 사용자 잔액 동시 처리)
```

#### @Version 동작 원리

```sql
-- UPDATE 시 자동으로 version 체크
UPDATE users
SET balance = 90000, version = 2
WHERE user_id = 'user-1' AND version = 1;

-- version이 일치하지 않으면 0 rows affected
-- JPA가 OptimisticLockException 발생
```

#### 동시성 테스트

```java
@Test
void chargeBalance_Concurrent_LostUpdate() {
    // 10번 동시 충전 (각 1만원)
    // 예상: 10만원 (낙관적 락으로 Lost Update 방지)
    assertThat(finalBalance).isEqualTo(100000L);
    assertThat(successCount.get()).isEqualTo(10);
}
```

---

### 📊 왜 이렇게 설계했는가?

#### 핵심 원칙: 경합 수준에 따른 락 전략 선택

```
┌─────────────────────────────────────────────────────────────┐
│ 경합 수준이 높을수록 비관적 락이 유리                          │
│ 경합 수준이 낮을수록 낙관적 락이 유리                          │
└─────────────────────────────────────────────────────────────┘

경합 수준별 전략:

높음 (선착순 쿠폰)  → 비관적 락
├─ 100명 중 1명만 성공하는 시나리오
├─ 낙관적 락 시 99명 재시도 → 시스템 과부하
└─ 비관적 락으로 순차 처리가 효율적

중상 (상품 재고)    → 비관적 락
├─ 인기 상품에 다수 동시 주문
├─ 초과 판매 시 비즈니스 리스크
└─ 정확성이 처리량보다 중요

낮음 (사용자 잔액)  → 낙관적 락
├─ 개인 리소스로 충돌 확률 낮음
├─ 충돌 시 재시도 비용 감수 가능
└─ 처리량과 응답 속도 우선
```

#### 직접 UPDATE 쿼리를 사용하는 이유

```
JPA dirty checking의 한계:
- @Embedded 객체(Stock, CouponStock)의 변경 감지 불안정
- save() 호출 후에도 UPDATE SQL이 생성되지 않는 경우 발생
- saveAndFlush()로 해결 가능하나 안티패턴

직접 UPDATE 쿼리의 장점:
1. 원자성 보장
   - UPDATE ... SET quantity = quantity - 1
   - DB 레벨에서 원자적 연산

2. JPA 영속성 컨텍스트 우회
   - dirty checking에 의존하지 않음
   - 100% 확실한 데이터 변경

3. 성능 최적화
   - 불필요한 SELECT 없이 바로 UPDATE
   - 네트워크 왕복 감소
```

#### 트레이드오프 분석

| 요소 | 비관적 락 | 낙관적 락 |
|------|----------|----------|
| 처리량 | 낮음 (순차 처리) | 높음 (병렬 처리) |
| 응답 시간 | 느림 (락 대기) | 빠름 (대기 없음) |
| 재시도 비용 | 없음 | 있음 (충돌 시) |
| 정확성 | 매우 높음 | 높음 |
| 데드락 위험 | 있음 | 없음 |
| 적합한 상황 | 고경합, 정확성 중요 | 저경합, 성능 중요 |

---

### 🧪 테스트 전략

#### 동시성 테스트 구성

```java
// 테스트 환경 설정
@Transactional(propagation = Propagation.NOT_SUPPORTED)
// → 각 스레드가 독립적인 트랜잭션 실행

// ExecutorService로 동시 요청 시뮬레이션
int threadCount = 100;
ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
CountDownLatch latch = new CountDownLatch(threadCount);

// 결과 검증
assertThat(successCount.get()).isEqualTo(expectedSuccess);
assertThat(failCount.get()).isEqualTo(expectedFail);
```

#### 테스트 케이스

1. **상품 재고 동시성**
   - 50명이 재고 100개 상품 동시 주문 (각 2개씩)
   - 100명이 재고 10개 상품 동시 주문

2. **쿠폰 발급 동시성**
   - 100명이 재고 50개 쿠폰 동시 발급
   - 같은 사용자가 10번 동시 발급 (중복 발급 테스트)
   - 10명이 재고 1개 쿠폰 동시 발급 (극한 경합)

3. **잔액 충전 동시성**
   - 10번 동시 충전 (Lost Update 테스트)
   - 100번 동시 충전 (대량 동시성)
   - 충전/차감 동시 실행 (혼합 테스트)
