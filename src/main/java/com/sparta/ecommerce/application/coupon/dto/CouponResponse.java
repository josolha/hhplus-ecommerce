package com.sparta.ecommerce.application.coupon.dto;

import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 쿠폰 응답 DTO
 */
public record CouponResponse(
        @Schema(description = "쿠폰 ID", example = "C001")
        String couponId,

        @Schema(description = "쿠폰명", example = "신규 가입 5만원 할인 쿠폰")
        String name,

        @Schema(description = "할인 타입", example = "FIXED")
        DiscountType discountType,

        @Schema(description = "할인 값 (FIXED: 금액, PERCENT: 퍼센트)", example = "50000")
        long discountValue,

        @Schema(description = "총 발급 수량", example = "100")
        int totalQuantity,

        @Schema(description = "남은 수량", example = "50")
        int remainingQuantity,

        @Schema(description = "최소 주문 금액", example = "100000")
        long minOrderAmount,

        @Schema(description = "만료 일시", example = "2025-12-31T23:59:59")
        LocalDateTime expiresAt
) {
    /**
     * Coupon 엔티티를 CouponResponse DTO로 변환
     */
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getCouponId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getStock().totalQuantity(),
                coupon.getStock().remainingQuantity(),
                coupon.getMinOrderAmount(),
                coupon.getExpiresAt()
        );
    }
}
