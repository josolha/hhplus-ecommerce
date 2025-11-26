# Redisson 분산 락 AOP로 리팩토링하기

## 1. 들어가며

이전 글에서 Redisson을 이용한 분산 락을 직접 구현하는 방식으로 쿠폰 발급 동시성 문제를 해결했다. 하지만 코드를 다시 보니 몇 가지 아쉬운 점이 보였다.

### 기존 방식의 문제점

```java
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {
    private final CouponIssueService couponIssueService;
    private final RedissonClient redissonClient;

    private static final String LOCK_KEY_PREFIX = "coupon:issue:";
    private static final long LOCK_WAIT_TIME = 10L;
    private static final long LOCK_LEASE_TIME = 3L;

    @Trace
    public UserCouponResponse execute(String userId, String couponId) {
        String lockKey = LOCK_KEY_PREFIX + couponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new CouponIssueLockException("쿠폰 발급 락 획득 실패");
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
- ✘ 비즈니스 로직과 락 처리 로직이 섞여 있다
- ✘ 락이 필요한 다른 기능을 만들 때마다 동일한 코드를 반복해야 한다
- ✘ 코드가 장황하고 핵심 로직이 보이지 않는다
- ✘ RedissonClient를 불필요하게 의존한다

마켓컬리 기술 블로그를 보다가 AOP 방식으로 분산 락을 처리하는 방법을 발견했다. 이 방식을 적용하면 위 문제들을 모두 해결할 수 있을 것 같았다.

---

## 2. AOP 방식의 장점

AOP(Aspect-Oriented Programming) 방식으로 전환하면 다음과 같은 장점이 있다.

**✔ 관심사의 분리**
- 비즈니스 로직과 락 처리 로직을 완전히 분리한다
- 각 계층의 책임이 명확해진다

**✔ 코드 재사용성**
- 어노테이션만 붙이면 분산 락을 적용할 수 있다
- 주문, 재고, 결제 등 다른 기능에도 즉시 적용 가능하다

**✔ 가독성 향상**
- 핵심 비즈니스 로직이 명확하게 보인다
- 락 처리 세부사항을 숨긴다

**✔ 유지보수성**
- 락 정책 변경 시 AOP 클래스 하나만 수정하면 된다
- 일관된 락 처리 방식을 강제할 수 있다

---

## 3. 구현 과정

### 3.1. @DistributedLock 어노테이션 생성

먼저 분산 락을 선언하기 위한 어노테이션을 만든다.

**위치:** `common/aop/annotation/DistributedLock.java`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락의 이름 (필수)
     */
    String key();

    /**
     * 락의 시간 단위 (기본: 초)
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 락을 기다리는 시간 (기본: 10초)
     * 락 획득을 위해 waitTime 만큼 대기한다
     */
    long waitTime() default 10L;

    /**
     * 락 임대 시간 (기본: 3초)
     * 락을 획득한 이후 leaseTime 이 지나면 락을 자동 해제한다
     */
    long leaseTime() default 3L;
}
```

**설계 포인트:**
- `key`: 락의 이름을 동적으로 지정할 수 있다 (SpEL 표현식 사용)
- `waitTime`, `leaseTime`: 기본값을 제공하되, 필요시 커스터마이징 가능하다
- `timeUnit`: 시간 단위를 유연하게 설정할 수 있다

---

### 3.2. CustomSpringELParser 생성

어노테이션의 `key` 파라미터에서 `#couponId` 같은 SpEL 표현식을 실제 값으로 변환하는 유틸리티가 필요하다.

**위치:** `common/util/CustomSpringELParser.java`

```java
public class CustomSpringELParser {

    private CustomSpringELParser() {
    }

    public static Object getDynamicValue(String[] parameterNames, Object[] args, String key) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(key).getValue(context, Object.class);
    }
}
```

**동작 원리:**

```java
// 메서드 호출
execute("user123", "COUPON-001")

// 파라미터 정보
parameterNames = ["userId", "couponId"]
args = ["user123", "COUPON-001"]
key = "#couponId"

// 파싱 결과
"#couponId" → "COUPON-001"
```

이를 통해 동적으로 락 키를 생성할 수 있다.

---

### 3.3. AopForTransaction 생성

분산 락 해제가 트랜잭션 커밋 이후에 발생하도록 보장하는 클래스다.

**위치:** `common/aop/AopForTransaction.java`

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

실행 순서를 보장하기 위해서다.

```
✔ 올바른 순서:
1. 락 획득
2. 트랜잭션 시작
3. 비즈니스 로직 실행
4. 트랜잭션 커밋 (DB 반영)
5. 락 해제

✘ 잘못된 순서:
1. 락 획득
2. 비즈니스 로직 실행
3. 락 해제 ← 트랜잭션 커밋 전에 해제됨
4. 트랜잭션 커밋
→ 다른 요청이 3번 시점에 락을 획득하면 아직 DB에 반영되지 않은 데이터를 조회하게 된다
```

`Propagation.REQUIRES_NEW`를 사용하여 부모 트랜잭션과 무관하게 새로운 트랜잭션을 시작한다. 이를 통해 트랜잭션 커밋이 락 해제보다 먼저 일어남을 보장한다.

---

### 3.4. DistributedLockAop 생성

핵심 AOP 클래스다. `@DistributedLock` 어노테이션이 붙은 메서드를 감싸서 락 획득/해제를 처리한다.

**위치:** `common/aop/DistributedLockAop.java`

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAop {

    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(com.sparta.ecommerce.common.aop.annotation.DistributedLock)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // 1. SpEL 파싱으로 동적 락 키 생성
        String key = REDISSON_LOCK_PREFIX + CustomSpringELParser.getDynamicValue(
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
                log.info("Redisson Lock Already UnLock - serviceName: {}, key: {}",
                        method.getName(), key);
            }
        }
    }
}
```

**동작 흐름:**
1. 어노테이션에서 락 설정 정보 추출
2. SpEL 파서로 동적 락 키 생성
3. 락 획득 시도 (대기 시간: waitTime)
4. 락 획득 성공 시 AopForTransaction을 통해 새 트랜잭션으로 비즈니스 로직 실행
5. 트랜잭션 커밋 후 락 해제
6. 이미 해제된 락을 unlock 하려는 시도는 로그만 남기고 무시

---

### 3.5. IssueCouponUseCase 리팩토링

기존 코드에서 락 처리 로직을 전부 제거하고 어노테이션만 추가한다.

**Before:**
```java
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {
    private final CouponIssueService couponIssueService;
    private final RedissonClient redissonClient;  // ← 불필요한 의존성

    private static final String LOCK_KEY_PREFIX = "coupon:issue:";
    private static final long LOCK_WAIT_TIME = 10L;
    private static final long LOCK_LEASE_TIME = 3L;

    @Trace
    public UserCouponResponse execute(String userId, String couponId) {
        String lockKey = LOCK_KEY_PREFIX + couponId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new CouponIssueLockException("쿠폰 발급 락 획득 실패");
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

**After:**
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

**변경 사항:**
- ✔ RedissonClient 의존성 제거
- ✔ 락 관련 상수 제거
- ✔ try-catch-finally 블록 제거
- ✔ 핵심 로직만 2줄로 표현
- ✔ 락 키를 SpEL로 동적 생성: `'coupon:issue:'.concat(#couponId)`

---

### 3.6. CouponIssueService 수정

AopForTransaction에서 `REQUIRES_NEW`로 새 트랜잭션을 시작하므로, 기존의 `@Transactional`을 제거한다.

**Before:**
```java
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    @Transactional  // ← 제거 필요
    public UserCouponResponse issue(String userId, String couponId) {
        // 비즈니스 로직
    }
}
```

**After:**
```java
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    public UserCouponResponse issue(String userId, String couponId) {
        // 비즈니스 로직 (변경 없음)
    }
}
```

---

## 4. 적용 효과

### 코드 라인 수 비교

| 구분 | Before | After | 감소율 |
|------|--------|-------|--------|
| IssueCouponUseCase | 35 라인 | 8 라인 | 77% 감소 |

### 재사용성 비교

**Before:**
```java
// 주문 생성에도 락이 필요하다면?
public class CreateOrderUseCase {
    private final RedissonClient redissonClient;

    public Order create(String orderId) {
        String lockKey = "order:create:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 또 반복되는 락 처리 코드...
        } finally {
            // ...
        }
    }
}
```

**After:**
```java
// 어노테이션 한 줄이면 끝
public class CreateOrderUseCase {

    @DistributedLock(key = "'order:create:'.concat(#orderId)")
    public Order create(String orderId) {
        // 비즈니스 로직만
    }
}
```

---

## 5. 테스트 검증

기존에 작성했던 동시성 테스트를 그대로 실행한다.

```bash
./gradlew test --tests IssueCouponConcurrencyTest
```

**결과:**
```
=== [Redisson 분산락] 쿠폰 동시성 제어 테스트 결과 ===
초기 재고: 50
동시 요청: 100명
발급 성공: 50
발급 실패: 50
Coupon.issuedQuantity: 50
Coupon.remainingQuantity: 0
실제 UserCoupon 개수: 50
✔ 분산락으로 동시성 제어 성공!
```

모든 테스트가 통과했다. AOP 방식으로 전환해도 동시성 제어가 정상적으로 동작한다.

---

## 6. 마치며

직접 구현 방식에서 AOP 방식으로 리팩토링하면서 얻은 것들:

**✔ 관심사의 분리**
- 비즈니스 로직과 인프라 관심사가 명확히 분리되었다
- 각 클래스의 책임이 단순해졌다

**✔ 코드 가독성**
- 핵심 로직이 명확하게 드러난다
- 락 처리 세부사항은 AOP에 위임했다

**✔ 재사용성**
- 주문, 재고, 결제 등 다른 기능에도 어노테이션만 붙이면 된다
- 일관된 락 처리 방식을 강제할 수 있다

**✔ 유지보수성**
- 락 정책 변경 시 AOP 클래스 하나만 수정하면 된다
- 락 획득 실패 처리, 로깅 등 공통 로직을 한 곳에서 관리한다

처음엔 직접 구현 방식으로 분산 락의 동작 원리를 이해했고, 이후 AOP로 리팩토링하면서 더 나은 구조를 만들 수 있었다. 학습 단계에서는 직접 구현으로 원리를 파악하고, 실무에서는 AOP 방식으로 생산성을 높이는 것이 좋은 접근이라고 생각한다.

---

## Reference

- [마켓컬리 기술 블로그: 분산락을 사용하는 방법](https://techblog.kurly.com/blog/distributed-redisson-lock/)
- [Redisson GitHub](https://github.com/redisson/redisson)
- [Redisson Guide](https://www.baeldung.com/redis-redisson)
- [Spring AOP Documentation](https://docs.spring.io/spring-framework/reference/core/aop.html)
