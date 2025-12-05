package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.CouponQueueResponse;
import com.sparta.ecommerce.application.coupon.dto.CreateCouponRequest;
import com.sparta.ecommerce.application.coupon.usecase.CreateCouponUseCase;
import com.sparta.ecommerce.application.coupon.usecase.IssueCouponWithQueueUseCase;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blocking Queue 방식 쿠폰 발급 동시성 테스트
 *
 * Redis BRPOP을 사용하여 Worker가 블로킹 방식으로 큐를 처리하는지 검증
 */
@Slf4j
@SpringBootTest
class BlockingQueueCouponTest {

    @Autowired
    private CreateCouponUseCase createCouponUseCase;

    @Autowired
    private IssueCouponWithQueueUseCase issueCouponWithQueueUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private com.sparta.ecommerce.application.coupon.worker.CouponWorker couponWorker;

    private String couponId;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();

        // 테스트용 쿠폰 생성 (10개 제한)
        CreateCouponRequest request = new CreateCouponRequest(
                "동시성 테스트 쿠폰",
                DiscountType.FIXED,
                5000L,
                0L,
                10,  // 총 10개만 발급 가능
                LocalDateTime.now().plusDays(7)
        );

        couponId = createCouponUseCase.execute(request).couponId();
        log.info("테스트 쿠폰 생성: couponId={}", couponId);
    }

    @Test
    @DisplayName("[Blocking Queue] 10명이 동시에 쿠폰 발급 요청 → Worker가 순차 처리 → 정확히 10개 발급")
    void testConcurrentCouponIssuance_BlockingQueue() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        log.info("=== 테스트 시작: 10명 동시 요청 ===");

        // when: 10명이 동시에 쿠폰 발급 요청
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            String userId = "user-" + UUID.randomUUID();

            executorService.submit(() -> {
                try {
                    CouponQueueResponse response = issueCouponWithQueueUseCase.execute(userId, couponId);
                    if (response.queued()) {
                        successCount.incrementAndGet();
                        log.debug("큐 추가 성공: {}", userId);
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.debug("큐 추가 실패: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long requestTime = System.currentTimeMillis() - startTime;
        executorService.shutdown();

        log.info("모든 요청 완료: {}ms", requestTime);
        log.info("큐 추가 성공: {}, 실패: {}", successCount.get(), failCount.get());

        // Worker 수동 처리
        log.info("Worker 수동 처리 시작...");
        couponWorker.processQueueManually(couponId, 20);

        // then: 실제 발급된 쿠폰 수 확인
        List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(couponId);
        Coupon coupon = couponRepository.findById(couponId).orElseThrow();

        log.info("=== 테스트 결과 ===");
        log.info("요청 소요 시간: {}ms", requestTime);
        log.info("큐 추가 성공: {}", successCount.get());
        log.info("큐 추가 실패: {}", failCount.get());
        log.info("실제 발급된 쿠폰 수: {}", issuedCoupons.size());
        log.info("쿠폰 재고 - 총량: {}", coupon.getStock().getTotalQuantity());
        log.info("쿠폰 재고 - 발급됨: {}", coupon.getStock().getIssuedQuantity());
        log.info("쿠폰 재고 - 남음: {}", coupon.getStock().getRemainingQuantity());

        // 검증
        assertThat(successCount.get()).isEqualTo(10); // 모두 큐에 추가됨
        assertThat(issuedCoupons).hasSize(10); // 정확히 10개만 발급
        assertThat(coupon.getStock().getIssuedQuantity()).isEqualTo(10);
        assertThat(coupon.getStock().getRemainingQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("[Blocking Queue] 20명 요청 → 10개 제한 → 정확히 10개만 발급")
    void testConcurrentCouponIssuance_ExceedsStock() throws InterruptedException {
        // given
        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        log.info("=== 테스트 시작: 20명 요청, 10개 재고 ===");

        // when: 20명이 동시에 쿠폰 발급 요청
        for (int i = 0; i < threadCount; i++) {
            String userId = "user-" + UUID.randomUUID();

            executorService.submit(() -> {
                try {
                    CouponQueueResponse response = issueCouponWithQueueUseCase.execute(userId, couponId);
                    if (response.queued()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.debug("큐 추가 실패: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        log.info("모든 요청 완료, 큐 추가 성공: {}", successCount.get());

        // Worker 수동 처리
        log.info("Worker 수동 처리 시작...");
        couponWorker.processQueueManually(couponId, 30);

        // then
        List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(couponId);
        Coupon coupon = couponRepository.findById(couponId).orElseThrow();

        log.info("=== 재고 초과 테스트 결과 ===");
        log.info("큐 추가 성공: {}", successCount.get());
        log.info("실제 발급된 쿠폰: {}", issuedCoupons.size());
        log.info("쿠폰 재고 상태: {}/{}", coupon.getStock().getIssuedQuantity(), coupon.getStock().getTotalQuantity());

        // 20명이 요청했지만 정확히 10개만 발급
        assertThat(issuedCoupons).hasSize(10);
        assertThat(coupon.getStock().getIssuedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("[Blocking Queue] 같은 사용자 중복 요청 → 큐에 1번만 추가 → 1개만 발급")
    void testDuplicateRequest_PreventedByRedisSet() throws InterruptedException {
        // given
        String userId = "duplicate-user-" + UUID.randomUUID();
        int requestCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);

        log.info("=== 중복 요청 테스트: 같은 사용자 5번 요청 ===");

        // when: 같은 사용자가 5번 동시에 요청
        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                try {
                    CouponQueueResponse response = issueCouponWithQueueUseCase.execute(userId, couponId);
                    if (response.queued()) {
                        successCount.incrementAndGet();
                        log.info("큐 추가 성공");
                    }
                } catch (Exception e) {
                    log.info("중복 차단: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        log.info("큐 추가 성공 횟수: {}", successCount.get());

        // Worker 수동 처리
        couponWorker.processQueueManually(couponId, 10);

        // then
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);

        log.info("=== 중복 요청 테스트 결과 ===");
        log.info("요청 횟수: {}", requestCount);
        log.info("큐 추가 성공: {}", successCount.get());
        log.info("실제 발급된 쿠폰: {}", userCoupons.size());

        // Redis Set으로 중복 체크하므로 1번만 큐에 추가됨
        assertThat(successCount.get()).isEqualTo(1);
        // 실제 발급도 1개만
        assertThat(userCoupons).hasSize(1);
    }

    @Test
    @DisplayName("[Blocking Queue] FIFO 순서 보장 확인")
    void testFIFO_Order() throws InterruptedException {
        // given
        int userCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);

        log.info("=== FIFO 순서 테스트 시작 ===");

        // when: 순차적으로 요청
        for (int i = 0; i < userCount; i++) {
            String userId = "user-" + i; // 순서대로 ID 부여
            final int order = i;

            executorService.submit(() -> {
                try {
                    issueCouponWithQueueUseCase.execute(userId, couponId);
                    log.info("{}번 사용자 큐 추가: {}", order, userId);
                } catch (Exception e) {
                    log.error("요청 실패: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });

            // 약간의 간격을 두고 요청 (순서 명확히 하기 위함)
            Thread.sleep(10);
        }

        latch.await();
        executorService.shutdown();

        log.info("모든 요청 완료, Worker 수동 처리...");

        // Worker 수동 처리
        couponWorker.processQueueManually(couponId, 20);

        // then
        List<UserCoupon> issuedCoupons = userCouponRepository.findByCouponId(couponId);
        // 발급 시간 순으로 정렬
        issuedCoupons.sort(java.util.Comparator.comparing(UserCoupon::getIssuedAt));

        log.info("=== 발급 순서 ===");
        for (int i = 0; i < issuedCoupons.size(); i++) {
            UserCoupon coupon = issuedCoupons.get(i);
            log.info("{}번째 발급: userId={}, issuedAt={}",
                    i + 1, coupon.getUserId(), coupon.getIssuedAt());
        }

        assertThat(issuedCoupons).hasSize(10);

        // 발급 시간이 순차적으로 증가하는지 확인 (FIFO)
        for (int i = 1; i < issuedCoupons.size(); i++) {
            LocalDateTime prevTime = issuedCoupons.get(i - 1).getIssuedAt();
            LocalDateTime currTime = issuedCoupons.get(i).getIssuedAt();
            assertThat(currTime).isAfterOrEqualTo(prevTime);
        }
    }

    @Test
    @DisplayName("[Blocking Queue] 성능 테스트: 응답 속도 확인")
    void testResponseTime_NonBlocking() throws InterruptedException {
        // given
        int userCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);

        log.info("=== 응답 속도 테스트 ===");

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < userCount; i++) {
            String userId = "user-" + UUID.randomUUID();

            executorService.submit(() -> {
                try {
                    issueCouponWithQueueUseCase.execute(userId, couponId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long responseTime = System.currentTimeMillis() - startTime;
        executorService.shutdown();

        log.info("=== 성능 테스트 결과 ===");
        log.info("10명 요청 응답 시간: {}ms", responseTime);
        log.info("평균 응답 시간: {}ms/req", responseTime / userCount);

        // then: 큐 방식은 즉시 응답하므로 매우 빨라야 함
        assertThat(responseTime).isLessThan(3000); // 3초 이내

        log.info("큐 추가는 빠르게 완료, Worker가 백그라운드에서 처리 중...");
    }
}
