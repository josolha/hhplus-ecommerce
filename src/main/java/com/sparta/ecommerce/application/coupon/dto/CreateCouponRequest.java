package com.sparta.ecommerce.application.coupon.dto;

import com.sparta.ecommerce.domain.coupon.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 쿠폰 생성 요청 DTO
 */
public record CreateCouponRequest(
        @Schema(description = "쿠폰명", example = "신규 회원 환영 쿠폰")
        @NotBlank(message = "쿠폰명은 필수입니다")
        String name,

        @Schema(description = "할인 타입 (FIXED: 정액, PERCENT: 정률)", example = "FIXED")
        @NotNull(message = "할인 타입은 필수입니다")
        DiscountType discountType,

        @Schema(description = "할인 값 (FIXED: 금액, PERCENT: 퍼센트)", example = "10000")
        @NotNull(message = "할인 값은 필수입니다")
        @Min(value = 1, message = "할인 값은 1 이상이어야 합니다")
        Long discountValue,

        @Schema(description = "최소 주문 금액", example = "50000")
        @NotNull(message = "최소 주문 금액은 필수입니다")
        @Min(value = 0, message = "최소 주문 금액은 0 이상이어야 합니다")
        Long minOrderAmount,

        @Schema(description = "발급 수량", example = "100")
        @NotNull(message = "발급 수량은 필수입니다")
        @Min(value = 1, message = "발급 수량은 1개 이상이어야 합니다")
        Integer totalQuantity,

        @Schema(description = "만료 일시", example = "2025-12-31T23:59:59")
        @NotNull(message = "만료 일시는 필수입니다")
        LocalDateTime expiresAt
) {
}
