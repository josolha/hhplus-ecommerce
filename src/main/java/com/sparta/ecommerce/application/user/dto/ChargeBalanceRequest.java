package com.sparta.ecommerce.application.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 잔액 충전 요청 DTO
 */
public record ChargeBalanceRequest(
        @Schema(description = "충전 금액", example = "50000")
        @NotNull(message = "충전 금액은 필수입니다")
        @Min(value = 1, message = "충전 금액은 1원 이상이어야 합니다")
        Long amount
) {
}
