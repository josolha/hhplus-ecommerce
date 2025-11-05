package com.sparta.ecommerce.application.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 잔액 충전 요청 DTO
 */
public record ChargeBalanceRequest(
        @NotNull(message = "충전 금액은 필수입니다")
        @Min(value = 1, message = "충전 금액은 1원 이상이어야 합니다")
        Long amount
) {
}
