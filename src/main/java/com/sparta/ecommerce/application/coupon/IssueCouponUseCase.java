package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.common.aop.annotation.Trace;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.CouponIssueLockException;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 쿠폰 발급 유스케이스
 *
 * 동시성 제어 전략:
 * - Redisson 분산 락 (Redis 기반)
 * - 락 키: "coupon:issue:{couponId}"
 * - 락 획득 대기 시간: 10초
 * - 락 자동 해제 시간: 3초 (데드락 방지)
 * - 다중 서버 환경에서 안전한 동시성 제어
 */
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponIssueService couponIssueService;
    private final RedissonClient redissonClient;

    // 분산 락 설정값
    private static final String LOCK_KEY_PREFIX = "coupon:issue:";
    private static final long LOCK_WAIT_TIME = 10L;  // 락 획득 대기 시간 (초)
    private static final long LOCK_LEASE_TIME = 3L;  // 락 자동 해제 시간 (초)

    /**
     * @throws InvalidCouponException 쿠폰이 존재하지 않을 때
     * @throws DuplicateCouponIssueException 이미 발급받은 쿠폰일 때
     * @throws CouponExpiredException 만료된 쿠폰일 때
     * @throws CouponSoldOutException 재고가 없을 때
     * @throws CouponIssueLockException 락 획득 실패
     */
    @Trace
    public UserCouponResponse execute(String userId, String couponId) {
        // 1. 락 키 생성
        String lockKey = LOCK_KEY_PREFIX + couponId;

        // 2. 락 객체 가져오기
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 3. 락 획득 시도 (10초 대기, 3초 자동 해제)
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!isLocked) {
                throw new CouponIssueLockException("쿠폰 발급 락 획득 실패");
            }

            // 4. 비즈니스 로직 실행 (트랜잭션)
            return couponIssueService.issue(userId, couponId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 획득 중 인터럽트 발생", e);
        } finally {
            // 5. 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
