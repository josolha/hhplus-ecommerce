# UserCoupon Dirty Checking 실패 이슈 해결 과정

## 문제 상황

주문 생성 프로세스에서 **UserCoupon의 `used_at` 필드가 DB에 업데이트되지 않는 문제**가 발생했습니다:

- 쿠폰 사용 처리 시 `userCoupon.use()` 호출로 메모리상에서는 `usedAt` 필드가 변경됨
- 하지만 트랜잭션 커밋 후에도 DB에는 `used_at` 컬럼이 여전히 `NULL`로 남아있음
- Hibernate 로그에서도 UPDATE 쿼리가 전혀 생성되지 않음

### 로그 증거

```
[2025-12-18 20:28:01] 쿠폰 사용 처리 - userCouponId=2e6ba6fd-114d-4cdb-aa46-67357c49d304, usedAt=null
[2025-12-18 20:28:01] 쿠폰 사용 처리 후 - userCouponId=2e6ba6fd-114d-4cdb-aa46-67357c49d304, usedAt=2025-12-18T20:28:01.352865
```

메모리에서는 `usedAt`이 변경되었지만, DB 조회 시 여전히 `NULL`:
```sql
SELECT * FROM user_coupons WHERE user_id = '...' AND coupon_id = '...';
-- used_at: NULL
```

## 문제 해결 시도 과정

### 시도 1: 엔티티 조회 방식 변경

**초기 코드:**
```java
// OrderFacade.java - 문제가 있던 코드
private void applyCoupon(String userId, String couponId) {
    if (couponId != null && !couponId.isEmpty()) {
        // stream().filter()로 조회 - 영속성 컨텍스트에 제대로 등록 안될 수 있음
        List<UserCoupon> coupons = userCouponRepository.findByUserId(userId);
        UserCoupon userCoupon = coupons.stream()
                .filter(uc -> uc.getCouponId().equals(couponId))
                .findFirst()
                .orElseThrow();

        userCoupon.use();
        // save() 호출 없음
    }
}
```

**첫 번째 개선:**
```java
// 직접 단건 조회로 변경
UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
        .orElseThrow(() -> new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다"));

userCoupon.use();
// 여전히 save() 호출 없음 - Dirty Checking에 의존
```

**결과:** 여전히 실패. 메모리에서는 `usedAt`이 변경되지만 DB UPDATE 쿼리가 생성되지 않음.

### 시도 2: Lombok 어노테이션 의심

```java
// UserCoupon.java
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)  // final 필드 생성 의심
public class UserCoupon {
    // ...
}
```

`@AllArgsConstructor`가 필드를 final로 만들어서 변경 불가능한 것은 아닌지 의심했으나, `javap` 명령어로 확인 결과 필드들은 final이 아니었습니다.

### 시도 3: @Modifying 쿼리 사용 (잘못된 접근)

```java
// UserCouponRepository.java - 억지로 만든 코드
@Modifying(clearAutomatically = true)
@Query("UPDATE UserCoupon uc SET uc.usedAt = :usedAt WHERE uc.userId = :userId AND uc.couponId = :couponId")
int updateUsedAt(@Param("userId") String userId,
                 @Param("couponId") String couponId,
                 @Param("usedAt") LocalDateTime usedAt);
```

이 접근은 근본 원인을 찾지 않고 우회하는 "억지" 해결책이었습니다. 실제 문제의 원인을 파악해야 했습니다.

## 근본 원인 발견

### 핵심 원인: @Modifying(clearAutomatically = true)

문제의 근본 원인은 `CartItemRepository`에 있었습니다:

```java
// CartItemRepository.java - 문제의 코드
@Modifying(clearAutomatically = true)
@Query("DELETE FROM CartItem c WHERE c.cartId = :cartId")
void deleteByCartId(@Param("cartId") String cartId);
```

`@Modifying(clearAutomatically = true)`는 벌크 연산 후 **영속성 컨텍스트를 자동으로 clear**합니다.

### 실행 순서 분석

```java
// OrderFacade.createOrder() 실행 순서
public OrderResult createOrder(String userId, String couponId) {
    // ... (1~7단계 생략)

    // 8. 쿠폰 사용 처리
    applyCoupon(userId, couponId);
    // -> UserCoupon 엔티티가 영속성 컨텍스트에 managed 상태로 존재
    // -> userCoupon.use() 호출로 usedAt 필드가 메모리에서 변경됨
    // -> 하지만 아직 DB에는 반영되지 않음 (Dirty Checking은 트랜잭션 커밋 시 발생)

    // 9. 장바구니 비우기
    cartItemRepository.deleteByCartId(cart.getCartId());
    // -> @Modifying(clearAutomatically = true) 때문에
    // -> DELETE 쿼리 실행 후 영속성 컨텍스트가 clear됨!
    // -> UserCoupon 엔티티가 영속성 컨텍스트에서 제거됨
    // -> usedAt 변경사항이 손실됨

    return new OrderResult(order, savedOrderItems);
    // 트랜잭션 커밋 시점에 Dirty Checking 발생하지만
    // UserCoupon은 이미 영속성 컨텍스트에 없으므로 UPDATE 쿼리가 생성되지 않음
}
```

### @Modifying 옵션 설명

- **`clearAutomatically = true`**: 벌크 연산 **후** 영속성 컨텍스트를 clear
  - 장점: 벌크 연산과 영속성 컨텍스트 간 불일치 방지
  - 단점: 영속성 컨텍스트의 모든 엔티티가 detached 상태가 됨 → Dirty Checking 불가

- **`flushAutomatically = true`**: 벌크 연산 **전** 영속성 컨텍스트를 flush
  - 장점: 벌크 연산 전에 영속성 컨텍스트의 변경사항을 먼저 DB에 반영
  - 장점: 영속성 컨텍스트는 유지됨 → Dirty Checking 정상 작동

## 최종 해결책

### 최종 해결: flushAutomatically 사용

```java
// CartItemRepository.java - 수정된 코드
@Modifying(flushAutomatically = true)  // clearAutomatically → flushAutomatically
@Query("DELETE FROM CartItem c WHERE c.cartId = :cartId")
void deleteByCartId(@Param("cartId") String cartId);
```

**동작 방식:**
1. 쿠폰 사용 처리: `userCoupon.use()` → 영속성 컨텍스트에 변경사항 기록
2. 장바구니 비우기 시작: `deleteByCartId()` 호출
3. **flush 발생**: UserCoupon의 변경사항이 먼저 DB에 UPDATE
4. DELETE 쿼리 실행: 장바구니 아이템 삭제
5. 영속성 컨텍스트 유지: 다른 엔티티들의 Dirty Checking도 정상 작동

**장점:**
- 원래 의도대로 JPA Dirty Checking을 활용
- 명시적 `save()` 호출 불필요
- 코드 변경 최소화
- UserCoupon의 `used_at`이 정상적으로 DB에 업데이트됨

## 대안 해결 방법

### 방법 2: Spring Data JPA 메서드 네이밍

```java
// CartItemRepository.java - 대안
void deleteByCartId(String cartId);  // @Query, @Modifying 제거
```

Spring Data JPA가 메서드 이름을 파싱해서 자동으로 DELETE 쿼리를 생성합니다.

**장점:**
- `@Modifying` 관련 문제 완전 회피
- 영속성 컨텍스트 자동 관리
- 코드가 더 간결

**단점:**
- 성능이 `@Query` 직접 작성보다 약간 느릴 수 있음 (각 엔티티를 조회 후 삭제)

### 방법 3: 실행 순서 변경

```java
// OrderFacade.java
public OrderResult createOrder(String userId, String couponId) {
    // ...

    // 9. 장바구니 비우기 (먼저)
    cartItemRepository.deleteByCartId(cart.getCartId());

    // 8. 쿠폰 사용 처리 (나중에)
    applyCoupon(userId, couponId);

    return new OrderResult(order, savedOrderItems);
}
```

장바구니를 먼저 비운 후 쿠폰을 사용하면, 영속성 컨텍스트 clear 후에 쿠폰이 조회되므로 문제 없습니다.

**단점:**
- 비즈니스 로직의 자연스러운 순서와 맞지 않음
- 향후 유사한 문제가 다시 발생할 수 있음

### 방법 4: 명시적 save() 호출

```java
// OrderFacade.java
private void applyCoupon(String userId, String couponId) {
    if (couponId != null && !couponId.isEmpty()) {
        UserCoupon userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 쿠폰을 찾을 수 없습니다"));

        userCoupon.use();
        userCouponRepository.save(userCoupon);  // 명시적 save()
    }
}
```

**단점:**
- 근본 원인을 해결하지 않음
- JPA의 Dirty Checking 메커니즘을 활용하지 못함
- 다른 엔티티에서도 같은 문제가 발생할 수 있음

## 배운 점

### 1. @Modifying 옵션의 영향 범위

`@Modifying(clearAutomatically = true)`는 해당 Repository 메서드뿐만 아니라 **전체 영속성 컨텍스트**에 영향을 미칩니다. 따라서:

- 트랜잭션 내에서 다른 엔티티의 Dirty Checking에도 영향
- 벌크 연산 후 다른 엔티티 작업이 있다면 주의 필요
- 가능하면 `flushAutomatically = true` 사용 권장

### 2. JPA Dirty Checking이 동작하지 않는 경우

Dirty Checking은 다음 조건을 **모두** 만족해야 동작합니다:

1. ✅ 엔티티가 managed 상태여야 함 (영속성 컨텍스트에 존재)
2. ✅ 트랜잭션 내에서 실행되어야 함 (`@Transactional`)
3. ✅ 엔티티의 필드 값이 변경되어야 함
4. ✅ **트랜잭션 커밋 시점까지 영속성 컨텍스트에 남아있어야 함** ← 이게 문제였음

### 3. 벌크 연산 사용 시 주의사항

벌크 연산 (`@Query`의 UPDATE/DELETE)은:
- JPQL로 직접 DB에 쿼리 실행
- 영속성 컨텍스트를 거치지 않음
- 영속성 컨텍스트와 DB 간 불일치 발생 가능
- 적절한 flush/clear 전략 필요

### 4. 디버깅 접근 방법

복잡한 JPA 이슈 디버깅 시:

1. **Hibernate 로그 확인** (application.yml):
   ```yaml
   logging:
     level:
       org.hibernate.SQL: debug
       org.hibernate.type.descriptor.sql.BasicBinder: trace
       org.hibernate.orm.jdbc.bind: trace
       org.hibernate.engine.transaction: debug
   ```

2. **실행 순서 추적**: 어떤 메서드가 언제 호출되는지 로그로 확인

3. **영속성 컨텍스트 상태 확인**: 엔티티가 managed/detached 중 어느 상태인지

4. **트랜잭션 경계 확인**: `@Transactional`이 올바르게 적용되었는지

5. **근본 원인 찾기**: 우회 해결책 말고 왜 문제가 발생하는지 원인 파악

## 결론

이번 이슈는 **벌크 연산의 영속성 컨텍스트 관리**와 **JPA Dirty Checking 메커니즘**을 깊이 이해하는 계기가 되었습니다.

단순히 `@Modifying` 어노테이션 하나의 옵션 차이가 전체 트랜잭션 내 다른 엔티티의 영속성에까지 영향을 미칠 수 있다는 점을 배웠고, 문제 해결 시 우회책보다는 근본 원인을 파악하는 것의 중요성을 깨달았습니다.

**최종 수정사항:**
- `CartItemRepository.deleteByCartId()`: `clearAutomatically = true` → `flushAutomatically = true`
- 이 한 줄의 변경으로 UserCoupon의 `used_at` 필드가 정상적으로 DB에 업데이트됨

## 참고 자료

- [Spring Data JPA @Modifying 공식 문서](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.modifying-queries)
- [Hibernate 영속성 컨텍스트 동작 원리](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#pc)
- [JPA Dirty Checking 메커니즘](https://vladmihalcea.com/jpa-persist-and-merge/)
