package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.usecase.IssueCouponWithQueueUseCase;
import com.sparta.ecommerce.application.coupon.worker.CouponWorker;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 큐 방식 쿠폰 발급 동시성 테스트
 */
@Slf4j
@SpringBootTest
class CouponQueueConcurrencyTest {

    @Autowired
    private IssueCouponWithQueueUseCase issueCouponWithQueueUseCase;

    @Autowired
    private CouponWorker couponWorker;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private String testCouponId; // 동적으로 생성되는 쿠폰 ID
    private static final int COUPON_QUANTITY = 100;
    private static final int CONCURRENT_USERS = 1000;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();

        // Redis 큐 정리
        Set<String> keys = redisTemplate.keys("coupon:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 테스트용 쿠폰 생성
        Coupon coupon = Coupon.builder()
                .name("큐 테스트 쿠폰")
                .discountType(com.sparta.ecommerce.domain.coupon.DiscountType.FIXED)
                .discountValue(10000L)
                .stock(new com.sparta.ecommerce.domain.coupon.vo.CouponStock(COUPON_QUANTITY, 0, COUPON_QUANTITY))
                .minOrderAmount(0L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        Coupon savedCoupon = couponRepository.save(coupon);
        testCouponId = savedCoupon.getCouponId(); // 생성된 ID 저장

        log.info("테스트 준비 완료: couponId={}, quantity={}", testCouponId, COUPON_QUANTITY);
    }

    @Test
    @DisplayName("Redis 큐: 1000명이 동시에 요청해도 100명만 발급 (비동기 처리)")
    void testQueueConcurrency() throws InterruptedException {
        // Given: 1000명의 사용자
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When: 1000명이 동시에 큐에 요청
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < CONCURRENT_USERS; i++) {
            String userId = "user_" + i;

            executorService.submit(() -> {
                try {
                    issueCouponWithQueueUseCase.execute(userId, testCouponId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.debug("큐 추가 실패: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long queueAddTime = System.currentTimeMillis() - startTime;

        log.info("큐 추가 완료: {}ms, 성공={}, 실패={}", queueAddTime, successCount.get(), failCount.get());

        // 큐에 추가된 요청 수 확인
        Long queueSize = redisTemplate.opsForList().size("coupon:queue:" + testCouponId);
        log.info("큐 크기: {}", queueSize);

        // Worker가 큐를 처리할 시간 대기 (충분한 시간 제공)
        log.info("Worker 처리 대기 중...");

        // Worker를 수동으로 실행하여 큐 처리
        int maxIterations = 200; // 최대 200번 시도 (20초)
        int processed = 0;

        for (int i = 0; i < maxIterations; i++) {
            int batchProcessed = couponWorker.processQueueManually(testCouponId, 10);
            processed += batchProcessed;

            if (batchProcessed == 0) {
                break; // 더 이상 처리할 게 없음
            }

            Thread.sleep(100); // 0.1초 대기
        }

        log.info("Worker 처리 완료: {} 건 처리됨", processed);

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);

        // Then: 발급된 쿠폰 수 확인
        long issuedCount = userCouponRepository.findByCouponId(testCouponId).size();
        Coupon updatedCoupon = couponRepository.findById(testCouponId).orElseThrow();

        log.info("=== 최종 결과 ===");
        log.info("큐 추가 시간: {}ms", queueAddTime);
        log.info("큐 추가 성공: {}", successCount.get());
        log.info("큐 추가 실패 (중복): {}", failCount.get());
        log.info("실제 발급된 쿠폰: {} 건", issuedCount);
        log.info("쿠폰 재고: {}/{}", updatedCoupon.getStock().getIssuedQuantity(), updatedCoupon.getStock().getTotalQuantity());

        // 검증
        assertThat(issuedCount).isEqualTo(COUPON_QUANTITY);
        assertThat(updatedCoupon.getStock().getIssuedQuantity()).isEqualTo(COUPON_QUANTITY);
    }

    @Test
    @DisplayName("Redis 큐: 중복 요청 방지 확인")
    void testDuplicatePrevention() throws InterruptedException {
        // Given: 같은 사용자가 여러 번 요청
        String userId = "duplicate_user";
        int requestCount = 10;

        AtomicInteger successCount = new AtomicInteger(0);

        // When: 동일 사용자가 10번 요청
        for (int i = 0; i < requestCount; i++) {
            try {
                issueCouponWithQueueUseCase.execute(userId, testCouponId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                log.debug("중복 요청 차단: {}", e.getMessage());
            }
        }

        // Worker 처리
        Thread.sleep(1000);
        couponWorker.processQueueManually(testCouponId, 100);

        // Then: 1개만 발급되어야 함
        long userCouponCount = userCouponRepository.findByUserId(userId).size();

        log.info("중복 요청 결과: 요청 {} 번, 큐 추가 성공 {} 번, 실제 발급 {} 건",
                requestCount, successCount.get(), userCouponCount);

        assertThat(successCount.get()).isEqualTo(1); // 큐에 1번만 추가
        assertThat(userCouponCount).isEqualTo(1); // 실제로도 1개만 발급
    }

    @Test
    @DisplayName("Redis 큐 vs 분산 락 성능 비교")
    void comparePerformance() throws InterruptedException {
        // Given
        int testUsers = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(50);

        // When: Redis 큐 방식
        CountDownLatch queueLatch = new CountDownLatch(testUsers);
        long queueStart = System.currentTimeMillis();

        for (int i = 0; i < testUsers; i++) {
            String userId = "queue_user_" + i;
            executorService.submit(() -> {
                try {
                    issueCouponWithQueueUseCase.execute(userId, testCouponId);
                } catch (Exception ignored) {
                } finally {
                    queueLatch.countDown();
                }
            });
        }

        queueLatch.await();
        long queueTime = System.currentTimeMillis() - queueStart;

        // Worker 처리 대기
        Thread.sleep(2000);
        couponWorker.processQueueManually(testCouponId, 100);

        // Then
        log.info("=== 성능 비교 ===");
        log.info("Redis 큐 방식 ({}명): {}ms", testUsers, queueTime);
        log.info("  - 큐 추가만: {}ms (즉시 응답)", queueTime);
        log.info("  - 실제 발급: 백그라운드 비동기 처리");

        executorService.shutdown();

        assertThat(queueTime).isLessThan(5000); // 5초 이내
    }
}
