package com.sparta.ecommerce.application.coupon.dto;

import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 사용자 쿠폰 응답 DTO
 */
public record UserCouponResponse(
        @Schema(description = "사용자 쿠폰 ID", example = "UC001")
        String userCouponId,

        @Schema(description = "사용자 ID", example = "U001")
        String userId,

        @Schema(description = "쿠폰 ID", example = "C001")
        String couponId,

        @Schema(description = "쿠폰명", example = "신규 가입 5만원 할인 쿠폰")
        String name,

        @Schema(description = "할인 타입", example = "FIXED")
        DiscountType discountType,

        @Schema(description = "할인 값", example = "50000")
        long discountValue,

        @Schema(description = "발급 일시", example = "2025-11-06T17:00:00")
        LocalDateTime issuedAt,

        @Schema(description = "사용 일시", example = "null")
        LocalDateTime usedAt,

        @Schema(description = "만료 일시", example = "2025-12-31T23:59:59")
        LocalDateTime expiresAt
) {
    /**
     * UserCoupon과 Coupon으로 UserCouponResponse 생성
     */
    public static UserCouponResponse from(UserCoupon userCoupon, Coupon coupon) {
        return new UserCouponResponse(
                userCoupon.getUserCouponId(),
                userCoupon.getUserId(),
                userCoupon.getCouponId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                userCoupon.getIssuedAt(),
                userCoupon.getUsedAt(),
                userCoupon.getExpiresAt()
        );
    }
}
