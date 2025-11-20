package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.IntegrationTestBase;
import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 동시성 테스트
 *
 * [목적]
 * - 선착순 쿠폰 발급 시 동시성 문제 확인
 * - 초과 발급 문제 재현
 * - 중복 발급 문제 재현
 *
 * [예상 결과]
 * - 이 테스트는 실패할 것으로 예상됨 (동시성 제어 미적용 상태)
 * - 재고보다 많이 발급되거나, 같은 사용자가 중복 발급받는 문제 발생 예상
 *
 * [주의]
 * - 동시성 테스트이므로 @Transactional 제거 (각 스레드가 독립적으로 트랜잭션 실행)
 *
 * [대안: Thread + Runnable 방식]
 * ExecutorService 대신 Thread와 Runnable을 직접 사용하는 방법:
 *
 * <pre>
 * // 방법 1: Thread + Runnable
 * int threadCount = 100;
 * CountDownLatch startLatch = new CountDownLatch(1);  // 동시 시작용
 * CountDownLatch endLatch = new CountDownLatch(threadCount);  // 종료 대기용
 * AtomicInteger successCount = new AtomicInteger(0);
 * AtomicInteger failCount = new AtomicInteger(0);
 *
 * List<Thread> threads = new ArrayList<>();
 *
 * for (int i = 0; i < threadCount; i++) {
 *     User user = testUsers.get(i);
 *     Thread thread = new Thread(new Runnable() {
 *         @Override
 *         public void run() {
 *             try {
 *                 startLatch.await();  // 모든 스레드가 준비될 때까지 대기
 *                 UserCouponResponse response = issueCouponUseCase.execute(
 *                         user.getUserId(),
 *                         limitedCoupon.getCouponId()
 *                 );
 *                 successCount.incrementAndGet();
 *             } catch (Exception e) {
 *                 failCount.incrementAndGet();
 *             } finally {
 *                 endLatch.countDown();
 *             }
 *         }
 *     });
 *     threads.add(thread);
 *     thread.start();
 * }
 *
 * startLatch.countDown();  // 모든 스레드 동시 시작!
 * endLatch.await();  // 모든 스레드 종료 대기
 *
 * // 방법 2: Lambda 사용 (더 간결)
 * for (int i = 0; i < threadCount; i++) {
 *     User user = testUsers.get(i);
 *     Thread thread = new Thread(() -> {
 *         try {
 *             startLatch.await();
 *             issueCouponUseCase.execute(user.getUserId(), limitedCoupon.getCouponId());
 *             successCount.incrementAndGet();
 *         } catch (Exception e) {
 *             failCount.incrementAndGet();
 *         } finally {
 *             endLatch.countDown();
 *         }
 *     });
 *     thread.start();
 * }
 *
 * startLatch.countDown();
 * endLatch.await();
 * </pre>
 *
 * [ExecutorService vs Thread 비교]
 * - ExecutorService: 스레드 풀 재사용, 관리 편함, 권장
 * - Thread 직접 생성: 스레드 생성/소멸 오버헤드, 교육용으로 적합
 */
public class IssueCouponConcurrencyTest extends IntegrationTestBase {

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    private Coupon limitedCoupon;
    private List<User> testUsers;

    @BeforeEach
    public void setUp() {

        // 1. 선착순 쿠폰 생성 (재고 50개)
        limitedCoupon = Coupon.builder()
                .name("선착순 5000원 할인쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(50, 0, 50))  // 총 50개
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        couponRepository.save(limitedCoupon);

        // 2. 테스트용 사용자 100명 생성
        testUsers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User user = User.builder()
                    .name("쿠폰테스트유저" + i)
                    .email("coupon-test" + i + "@example.com")
                    .balance(new Balance(100000L))
                    .build();
            userRepository.save(user);
            testUsers.add(user);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[동시성 문제 재현] 100명이 재고 50개 쿠폰 동시 발급 시 초과 발급 발생")
    void issueCoupon_ConcurrentIssue_Overselling() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 쿠폰 발급 시도 (하지만 재고는 50개)
        for (User user : testUsers) {
            executorService.execute(() -> {
                try {
                    UserCouponResponse response = issueCouponUseCase.execute(
                            user.getUserId(),
                            limitedCoupon.getCouponId()
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("발급 실패 (" + Thread.currentThread().getName() + "): " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        entityManager.clear();
        Coupon updatedCoupon = couponRepository.findById(limitedCoupon.getCouponId()).get();
        int issuedQuantity = updatedCoupon.getStock().issuedQuantity();
        int remainingQuantity = updatedCoupon.getStock().remainingQuantity();

        // 실제 발급된 UserCoupon 개수 확인
        long actualIssuedCount = userCouponRepository.findByCouponId(limitedCoupon.getCouponId()).size();

        System.out.println("=== 쿠폰 초과 발급 테스트 결과 ===");
        System.out.println("초기 재고: 50");
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("발급 실패: " + failCount.get());
        System.out.println("Coupon.issuedQuantity: " + issuedQuantity);
        System.out.println("Coupon.remainingQuantity: " + remainingQuantity);
        System.out.println("실제 UserCoupon 개수: " + actualIssuedCount);

        // 동시성 제어가 없다면:
        // - 50개 이상 발급될 수 있음
        // - issuedQuantity와 actualIssuedCount가 다를 수 있음 (Lost Update)

        // 올바른 동작:
        // - 성공 = 50개
        // - 실패 = 50개
        // - issuedQuantity = 50
        // - remainingQuantity = 0
        // - actualIssuedCount = 50

        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);
        assertThat(issuedQuantity).isEqualTo(50);
        assertThat(remainingQuantity).isEqualTo(0);
        assertThat(actualIssuedCount).isEqualTo(50);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[동시성 문제 재현] 같은 사용자가 동시에 10번 발급 시도 시 중복 발급 발생")
    void issueCoupon_SameUser_DuplicateIssue() throws InterruptedException {
        // given - 단일 사용자
        User singleUser = testUsers.get(0);

        int threadCount = 10;  // 같은 사용자가 10개 스레드에서 동시 발급 시도
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 같은 사용자가 동시에 10번 발급 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    UserCouponResponse response = issueCouponUseCase.execute(
                            singleUser.getUserId(),
                            limitedCoupon.getCouponId()
                    );
                    successCount.incrementAndGet();
                    System.out.println("발급 성공: " + Thread.currentThread().getName());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("발급 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        entityManager.clear();

        // 해당 사용자가 발급받은 쿠폰 개수 확인
        long userCouponCount = userCouponRepository.findByUserId(singleUser.getUserId()).stream()
                .filter(uc -> uc.getCouponId().equals(limitedCoupon.getCouponId()))
                .count();

        System.out.println("=== 중복 발급 테스트 결과 ===");
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("발급 실패: " + failCount.get());
        System.out.println("실제 발급된 UserCoupon 개수: " + userCouponCount);

        // 동시성 제어가 없다면:
        // - 중복 발급이 발생할 수 있음 (userCouponCount > 1)

        // 올바른 동작:
        // - 성공 = 1개
        // - 실패 = 9개
        // - userCouponCount = 1

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
        assertThat(userCouponCount).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[동시성 문제 재현] 재고 1개 쿠폰에 10명 동시 발급 시 초과 발급")
    void issueCoupon_LastOne_RaceCondition() throws InterruptedException {
        // given - 재고 1개만 남은 쿠폰
        Coupon lastOneCoupon = Coupon.builder()
                .name("마지막 1개 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(1, 0, 1))  // 재고 1개
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        couponRepository.save(lastOneCoupon);

        //entityManager.flush();
        entityManager.clear();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 10명이 동시에 발급 시도 (하지만 재고 1개)
        for (int i = 0; i < threadCount; i++) {
            User user = testUsers.get(i);
            executorService.execute(() -> {
                try {
                    UserCouponResponse response = issueCouponUseCase.execute(
                            user.getUserId(),
                            lastOneCoupon.getCouponId()
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        entityManager.clear();
        Coupon updatedCoupon = couponRepository.findById(lastOneCoupon.getCouponId()).get();
        long actualIssuedCount = userCouponRepository.findByCouponId(lastOneCoupon.getCouponId()).size();

        System.out.println("=== 마지막 1개 경쟁 테스트 결과 ===");
        System.out.println("초기 재고: 1");
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("발급 실패: " + failCount.get());
        System.out.println("실제 발급 개수: " + actualIssuedCount);
        System.out.println("Coupon.issuedQuantity: " + updatedCoupon.getStock().issuedQuantity());

        // 올바른 동작:
        // - 성공 = 1
        // - 실패 = 9
        // - actualIssuedCount = 1

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
        assertThat(actualIssuedCount).isEqualTo(1);
    }
}
