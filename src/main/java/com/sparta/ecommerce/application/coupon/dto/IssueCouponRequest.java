package com.sparta.ecommerce.application.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 쿠폰 발급 요청 DTO
 */
public record IssueCouponRequest(
        @Schema(description = "사용자 ID", example = "U001")
        @NotBlank(message = "사용자 ID는 필수입니다")
        String userId
) {
}
