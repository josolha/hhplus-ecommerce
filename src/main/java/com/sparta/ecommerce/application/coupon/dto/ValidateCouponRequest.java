package com.sparta.ecommerce.application.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 쿠폰 유효성 검증 요청 DTO
 */
public record ValidateCouponRequest(
        @Schema(description = "쿠폰 ID", example = "C001")
        @NotBlank(message = "쿠폰 ID는 필수입니다")
        String couponId,

        @Schema(description = "주문 금액", example = "150000")
        @Positive(message = "주문 금액은 0보다 커야 합니다")
        int orderAmount
) {
}
