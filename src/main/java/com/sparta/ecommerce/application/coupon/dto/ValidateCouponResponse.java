package com.sparta.ecommerce.application.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 쿠폰 유효성 검증 응답 DTO
 */
public record ValidateCouponResponse(
        @Schema(description = "유효 여부", example = "true")
        boolean valid,

        @Schema(description = "할인 금액", example = "50000")
        long discountAmount,

        @Schema(description = "최종 결제 금액 (주문금액 - 할인금액)", example = "100000")
        long finalAmount,

        @Schema(description = "메시지", example = "쿠폰이 정상적으로 적용되었습니다")
        String message
) {
    /**
     * 유효한 쿠폰 응답 생성
     */
    public static ValidateCouponResponse valid(int orderAmount, long discountAmount) {
        return new ValidateCouponResponse(
                true,
                discountAmount,
                orderAmount - discountAmount,
                "쿠폰이 정상적으로 적용되었습니다"
        );
    }

    /**
     * 유효하지 않은 쿠폰 응답 생성
     */
    public static ValidateCouponResponse invalid(String message) {
        return new ValidateCouponResponse(
                false,
                0,
                0,
                message
        );
    }
}
