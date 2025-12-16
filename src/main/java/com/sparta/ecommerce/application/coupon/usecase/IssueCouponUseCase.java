package com.sparta.ecommerce.application.coupon.usecase;

import com.sparta.ecommerce.application.coupon.service.CouponIssueService;
import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.infrastructure.aop.annotation.DistributedLock;
import com.sparta.ecommerce.infrastructure.aop.annotation.Trace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 발급 유스케이스
 *
 * 동시성 제어 전략:
 * - Redisson 분산 락 (Redis 기반)
 * - 락 키: "lock:coupon:issue:{couponId}"
 * - 락 획득 대기 시간: 10초
 * - 락 자동 해제 시간: 3초
 * - 다중 서버 환경에서 안전한 동시성 제어
 */
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