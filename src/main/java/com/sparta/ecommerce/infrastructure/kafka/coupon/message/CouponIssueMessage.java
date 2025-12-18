package com.sparta.ecommerce.infrastructure.kafka.coupon.message;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 요청 메시지
 *
 * Kafka Topic: coupon-issue-request
 * 메시지 키: couponId (같은 쿠폰은 같은 파티션으로 라우팅)
 *
 * 필드 설명:
 * - couponId: 쿠폰 ID (파티션 키로 사용)
 * - userId: 사용자 ID
 * - requestedAt: 요청 시각 (모니터링 및 디버깅용)
 */
public record CouponIssueMessage(
        String couponId,
        String userId,
        LocalDateTime requestedAt
) {
    /**
     * 팩토리 메서드: 현재 시각으로 메시지 생성
     */
    public static CouponIssueMessage of(String couponId, String userId) {
        return new CouponIssueMessage(couponId, userId, LocalDateTime.now());
    }
}
