package com.sparta.ecommerce.application.coupon.worker;

import com.sparta.ecommerce.application.coupon.service.CouponIssueProcessor;
import com.sparta.ecommerce.application.coupon.service.CouponQueueService;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 쿠폰 발급 큐 처리 Worker (Blocking Queue 방식)
 *
 * Redis BRPOP으로 큐를 블로킹하며 대기
 * 데이터가 들어오면 즉시 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponWorker {

    private final CouponQueueService queueService;
    private final CouponIssueProcessor couponIssueProcessor;
    private final CouponRepository couponRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    /**
     * 애플리케이션 시작 시 Worker 스레드 실행
     */
    @PostConstruct
    public void startWorkers() {
        log.info("쿠폰 큐 Worker 시작");

        // 활성 쿠폰 조회
        List<Coupon> activeCoupons = couponRepository.findAvailableCoupons(LocalDateTime.now());

        // 각 쿠폰마다 Worker 스레드 실행
        for (Coupon coupon : activeCoupons) {
            startWorkerForCoupon(coupon.getCouponId());
        }
    }

    /**
     * 애플리케이션 종료 시 Worker 스레드 정리
     */
    @PreDestroy
    public void shutdown() {
        log.info("쿠폰 큐 Worker 종료 시작");

        executorService.shutdown();  // 새 작업 받지 않음

        try {
            // 최대 5초 대기
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Worker가 5초 안에 종료되지 않아 강제 종료합니다");
                executorService.shutdownNow();  // 강제 종료
            }
            log.info("쿠폰 큐 Worker 종료 완료");
        } catch (InterruptedException e) {
            log.error("Worker 종료 중 인터럽트 발생", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 특정 쿠폰의 Worker 스레드 시작
     *
     * @param couponId 쿠폰 ID
     */
    public void startWorkerForCoupon(String couponId) {
        executorService.submit(() -> {
            log.info("Worker 시작: couponId={}", couponId);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Blocking Pop (최대 5초 대기)
                    String userId = queueService.blockingPopFromQueue(couponId);

                    if (userId != null) {
                        // 쿠폰 발급 처리 (트랜잭션 서비스를 통해 호출)
                        couponIssueProcessor.processSingleIssue(userId, couponId);
                    }

                } catch (org.redisson.RedissonShutdownException e) {
                    log.info("Worker 종료 (Redisson shutdown): couponId={}", couponId);
                    break;  // 정상 종료

                } catch (org.redisson.client.RedisException e) {
                    // InterruptedException이 원인인 경우 정상 종료
                    if (e.getCause() instanceof InterruptedException) {
                        log.info("Worker 종료 (Interrupted): couponId={}", couponId);
                        Thread.currentThread().interrupt();
                        break;
                    }
                    log.error("Worker Redis 예외 발생: couponId={}", couponId, e);

                } catch (Exception e) {
                    // 기타 예외 처리
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("Worker 종료 (Thread interrupted): couponId={}", couponId);
                        break;
                    }
                    log.error("Worker 예외 발생: couponId={}", couponId, e);
                    // 잠시 대기 후 재시도
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        log.info("Worker 종료 (Sleep interrupted): couponId={}", couponId);
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("Worker 종료: couponId={}", couponId);
        });
    }


    /**
     * 특정 쿠폰의 큐를 수동으로 처리 (관리자 기능)
     *
     * @param couponId 쿠폰 ID
     * @param maxCount 최대 처리 개수
     * @return 처리된 개수
     */
    public int processQueueManually(String couponId, int maxCount) {
        int processed = 0;

        for (int i = 0; i < maxCount; i++) {
            String userId = queueService.popFromQueue(couponId);

            if (userId == null) {
                break;
            }

            couponIssueProcessor.processSingleIssue(userId, couponId);
            processed++;
        }

        log.info("수동 큐 처리 완료: couponId={}, processed={}", couponId, processed);
        return processed;
    }
}
