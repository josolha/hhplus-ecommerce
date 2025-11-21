## :pushpin: PR 제목 규칙
[STEP10] 동시성 제어 구현 - e-commerce

---

## :clipboard: 핵심 체크리스트 :white_check_mark:

### STEP09 - Concurrency (2개)
- [x] 애플리케이션 내에서 발생 가능한 **동시성 문제를 식별**했는가?
- [x] 보고서에 DB를 활용한 **동시성 문제 해결 방안**이 포함되어 있는가?

---

### STEP10 - Finalize (1개)
- [x] **동시성 문제를 드러낼 수 있는 통합 테스트**를 작성했는가?

---

## 📝 작업 내용

### 동시성 제어 전략

| 리소스 | 락 유형 | 경합 수준 | 선택 이유 |
|--------|---------|-----------|-----------|
| 상품 재고 | 비관적 락 + 직접 UPDATE | 높음 | 다수 사용자가 동일 상품 주문, 초과 판매 방지 필수 |
| 쿠폰 재고 | 비관적 락 + 직접 UPDATE | 매우 높음 | 선착순 발급, 중복/초과 발급 방지 필수 |
| 사용자 잔액 | 낙관적 락 (@Version) | 낮음 | 개인 리소스, 충돌 확률 낮음 |

### 핵심 원칙
- **경합 수준이 높을수록 비관적 락** (재시도 비용 > 락 대기 비용)
- **경합 수준이 낮을수록 낙관적 락** (처리량 우선)

### 구현 파일
- `ProductRepository.java` - `findByIdWithLock()`, `decreaseStock()`
- `CouponRepository.java` - `findByIdWithLock()`, `issueCoupon()`
- `UserCouponRepository.java` - `existsByUserIdAndCouponIdWithLock()`
- `IssueCouponUseCase.java` - 비관적 락 적용
- `PaymentService.java` - 낙관적 락으로 변경
- `OrderFacade.java` - 직접 UPDATE 쿼리 사용

### 테스트 파일
- `CreateOrderConcurrencyTest.java` - 상품 재고 동시성 테스트
- `IssueCouponConcurrencyTest.java` - 쿠폰 발급 동시성 테스트
- `ChargeBalanceConcurrencyTest.java` - 잔액 충전 동시성 테스트

---

## ❓ 질문 사항

### JPA @Embedded 객체의 dirty checking이 동작하지 않는 문제

**문제 상황:**
상품 재고를 차감할 때 `save()` 호출 후에도 UPDATE SQL이 생성되지 않는 현상이 발생했습니다.

```java
// Stock은 @Embedded Value Object
Product product = productRepository.findByIdWithLock(productId);
product.decreaseStock(quantity);  // Stock 객체 내부 값 변경
productRepository.save(product);  // UPDATE SQL이 생성되지 않음!
```

**시도한 해결책들:**
1. Stock을 record → class로 변경 ❌
2. Stock을 불변 객체로 만들고 새 객체로 교체 ❌
3. @DynamicUpdate 적용 ❌
4. @Transactional 제거 후 다시 적용 ❌
5. `saveAndFlush()` 사용 → 동작하지만 안티패턴

**최종 해결:**
직접 UPDATE 쿼리를 사용하여 JPA 영속성 컨텍스트를 우회했습니다.

```java
@Modifying
@Query("UPDATE Product p SET p.stock.quantity = p.stock.quantity - :amount WHERE p.productId = :productId")
int decreaseStock(@Param("productId") String productId, @Param("amount") int amount);
```

**질문:**
1. JPA @Embedded 객체의 dirty checking이 불안정한 이유가 무엇인가요?
2. 이런 상황에서 직접 UPDATE 쿼리를 사용하는 것이 올바른 해결책인가요?
3. 다른 더 나은 방법이 있을까요?

---

## ✍️ 간단 회고 (3줄 이내)
- **잘한 점**: 경합 수준에 따라 비관적/낙관적 락을 적절히 선택하여 각 리소스에 맞는 동시성 제어를 구현함
- **어려웠던 점**: JPA @Embedded 객체의 dirty checking 문제로 재고 차감이 되지 않아 원인 파악에 많은 시간 소요
- **다음 시도**: Redis 분산 락을 활용한 쿠폰 발급 최적화, 낙관적 락 재시도 로직 추가
