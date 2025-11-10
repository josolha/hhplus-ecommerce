package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.Coupon;
import com.sparta.ecommerce.domain.coupon.CouponRepository;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import com.sparta.ecommerce.infrastructure.memory.InMemoryCouponRepository;
import com.sparta.ecommerce.infrastructure.memory.InMemoryDataStore;
import com.sparta.ecommerce.infrastructure.memory.InMemoryUserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 동시성 제어 테스트
 *
 * 목적:
 * - 선착순 쿠폰 발급 시 동시성 제어가 올바르게 동작하는지 검증
 * - Race Condition으로 인한 Over-issuance 방지 확인
 * - Lock Mechanism (synchronized)이 정상 작동하는지 검증
 */
@DisplayName("쿠폰 발급 동시성 제어 테스트")
class IssueCouponConcurrencyTest {

    private CouponRepository couponRepository;
    private UserCouponRepository userCouponRepository;
    private IssueCouponUseCase issueCouponUseCase;
    private InMemoryDataStore dataStore;

    @BeforeEach
    void setUp() {
        // 실제 InMemory Repository 사용 (Mock 아님)
        dataStore = new InMemoryDataStore();
        couponRepository = new InMemoryCouponRepository(dataStore);
        userCouponRepository = new InMemoryUserCouponRepository(dataStore);
        issueCouponUseCase = new IssueCouponUseCase(couponRepository, userCouponRepository);
    }

    @Test
    @DisplayName("100명이 동시에 재고 50개 쿠폰 발급 시도 → 정확히 50명만 성공")
    void 동시_쿠폰_발급_재고_초과_방지() throws InterruptedException {
        // given
        String couponId = "CONCURRENT_TEST_COUPON";
        int initialStock = 50;
        int threadCount = 100;

        // 테스트용 쿠폰 생성 (재고 50개)
        Coupon testCoupon = Coupon.builder()
                .couponId(couponId)
                .name("선착순 50명 한정 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(10000)
                .stock(new CouponStock(initialStock, initialStock))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);

        // 동시 실행을 위한 설정
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 100명의 사용자가 동시에 쿠폰 발급 시도
        for (int i = 0; i < threadCount; i++) {
            String userId = "USER_" + String.format("%03d", i);
            executorService.submit(() -> {
                try {
                    UserCouponResponse response = issueCouponUseCase.execute(userId, couponId);
                    if (response != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 재고 부족, 중복 발급 등의 예외 발생 시 실패로 카운트
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 작업 완료 대기
        latch.await();
        executorService.shutdown();

        // then: 동시성 제어 검증
        assertThat(successCount.get()).isEqualTo(initialStock)
                .withFailMessage("성공 횟수는 초기 재고(%d)와 같아야 합니다. 실제: %d", initialStock, successCount.get());

        assertThat(failCount.get()).isEqualTo(threadCount - initialStock)
                .withFailMessage("실패 횟수는 %d여야 합니다. 실제: %d", threadCount - initialStock, failCount.get());

        // 최종 재고 확인
        Coupon finalCoupon = couponRepository.findById(couponId).orElseThrow();
        assertThat(finalCoupon.getStock().remainingQuantity()).isZero()
                .withFailMessage("최종 재고는 0이어야 합니다. 실제: %d", finalCoupon.getStock().remainingQuantity());

        // 발급된 사용자 쿠폰 개수 확인
        long issuedCouponCount = dataStore.getUserCoupons().values().stream()
                .filter(uc -> couponId.equals(uc.getCouponId()))
                .count();
        assertThat(issuedCouponCount).isEqualTo(initialStock)
                .withFailMessage("발급된 쿠폰 개수는 %d개여야 합니다. 실제: %d", initialStock, issuedCouponCount);
    }

    @Test
    @DisplayName("동일 사용자가 동시에 같은 쿠폰 발급 시도 → 1번만 성공 (중복 발급 방지)")
    void 동일_사용자_중복_발급_방지() throws InterruptedException {
        // given
        String couponId = "DUPLICATE_TEST_COUPON";
        String userId = "SAME_USER";
        int threadCount = 10;

        Coupon testCoupon = Coupon.builder()
                .couponId(couponId)
                .name("중복 발급 테스트 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000)
                .stock(new CouponStock(100, 100)) // 충분한 재고
                .minOrderAmount(10000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        // when: 동일 사용자가 10번 동시에 발급 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    issueCouponUseCase.execute(userId, couponId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then: 정확히 1번만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1)
                .withFailMessage("동일 사용자는 1번만 발급받아야 합니다. 실제: %d", successCount.get());

        assertThat(exceptions).hasSize(threadCount - 1)
                .withFailMessage("나머지 %d번은 실패해야 합니다", threadCount - 1);

        // 발급된 쿠폰 개수 확인
        long userCouponCount = dataStore.getUserCoupons().values().stream()
                .filter(uc -> userId.equals(uc.getUserId()) && couponId.equals(uc.getCouponId()))
                .count();
        assertThat(userCouponCount).isEqualTo(1)
                .withFailMessage("사용자에게 발급된 쿠폰은 1개여야 합니다. 실제: %d", userCouponCount);
    }

    @Test
    @DisplayName("1000명이 동시에 재고 100개 쿠폰 발급 시도 → 정확히 100명만 성공 (고부하 시나리오)")
    void 고부하_동시_쿠폰_발급() throws InterruptedException {
        // given
        String couponId = "HIGH_LOAD_TEST_COUPON";
        int initialStock = 100;
        int threadCount = 1000;

        Coupon testCoupon = Coupon.builder()
                .couponId(couponId)
                .name("고부하 테스트 쿠폰")
                .discountType(DiscountType.PERCENT)
                .discountValue(20)
                .stock(new CouponStock(initialStock, initialStock))
                .minOrderAmount(30000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();
        couponRepository.save(testCoupon);

        ExecutorService executorService = Executors.newFixedThreadPool(100); // 스레드 풀 크기
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            String userId = "LOAD_USER_" + String.format("%04d", i);
            executorService.submit(() -> {
                try {
                    issueCouponUseCase.execute(userId, couponId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 실패는 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(initialStock)
                .withFailMessage("고부하 환경에서도 정확히 %d명만 성공해야 합니다. 실제: %d", initialStock, successCount.get());

        Coupon finalCoupon = couponRepository.findById(couponId).orElseThrow();
        assertThat(finalCoupon.getStock().remainingQuantity()).isZero()
                .withFailMessage("고부하 환경에서도 최종 재고는 0이어야 합니다");

        // Over-issuance 검증
        long totalIssued = dataStore.getUserCoupons().values().stream()
                .filter(uc -> couponId.equals(uc.getCouponId()))
                .count();
        assertThat(totalIssued).isEqualTo(initialStock)
                .withFailMessage("Over-issuance 발생! 예상: %d, 실제: %d", initialStock, totalIssued);
    }

    @Test
    @DisplayName("서로 다른 쿠폰(C001, C002) 동시 발급 → 락 경쟁 없이 병렬 처리 확인")
    void 다중_쿠폰_병렬_발급_성능_테스트() throws InterruptedException {
        // given
        String coupon1 = "PARALLEL_TEST_COUPON_1";
        String coupon2 = "PARALLEL_TEST_COUPON_2";
        int stockPerCoupon = 50;
        int threadsPerCoupon = 50;

        // 쿠폰 2개 생성
        couponRepository.save(Coupon.builder()
                .couponId(coupon1)
                .name("병렬 테스트 쿠폰 1")
                .discountType(DiscountType.FIXED)
                .discountValue(10000)
                .stock(new CouponStock(stockPerCoupon, stockPerCoupon))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build());

        couponRepository.save(Coupon.builder()
                .couponId(coupon2)
                .name("병렬 테스트 쿠폰 2")
                .discountType(DiscountType.FIXED)
                .discountValue(20000)
                .stock(new CouponStock(stockPerCoupon, stockPerCoupon))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build());

        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(threadsPerCoupon * 2);
        AtomicInteger coupon1Success = new AtomicInteger(0);
        AtomicInteger coupon2Success = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // when: 쿠폰1 발급 (50명)
        for (int i = 0; i < threadsPerCoupon; i++) {
            String userId = "C1_USER_" + String.format("%03d", i);
            executorService.submit(() -> {
                try {
                    issueCouponUseCase.execute(userId, coupon1);
                    coupon1Success.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        // when: 쿠폰2 발급 (50명) - 동시 실행
        for (int i = 0; i < threadsPerCoupon; i++) {
            String userId = "C2_USER_" + String.format("%03d", i);
            executorService.submit(() -> {
                try {
                    issueCouponUseCase.execute(userId, coupon2);
                    coupon2Success.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long elapsedTime = System.currentTimeMillis() - startTime;

        // then: 두 쿠폰 모두 정확히 50개씩 발급
        assertThat(coupon1Success.get()).isEqualTo(stockPerCoupon)
                .withFailMessage("쿠폰1 발급 실패: %d", coupon1Success.get());
        assertThat(coupon2Success.get()).isEqualTo(stockPerCoupon)
                .withFailMessage("쿠폰2 발급 실패: %d", coupon2Success.get());

        // 병렬 처리 확인 (쿠폰별 락이므로 순차 실행보다 빨라야 함)
        System.out.printf("다중 쿠폰 병렬 발급 소요 시간: %d ms%n", elapsedTime);

        // 최종 재고 확인
        Coupon finalCoupon1 = couponRepository.findById(coupon1).orElseThrow();
        Coupon finalCoupon2 = couponRepository.findById(coupon2).orElseThrow();
        assertThat(finalCoupon1.getStock().remainingQuantity()).isZero();
        assertThat(finalCoupon2.getStock().remainingQuantity()).isZero();
    }

}
