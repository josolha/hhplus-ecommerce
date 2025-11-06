package com.sparta.ecommerce.application.user.dto;

import com.sparta.ecommerce.domain.user.User;
import com.sparta.ecommerce.domain.user.vo.Balance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 잔액 충전 응답 DTO
 */
public record ChargeBalanceResponse(
        @Schema(description = "사용자 ID", example = "U001")
        String userId,

        @Schema(description = "충전 전 잔액", example = "2000000")
        Long previousBalance,

        @Schema(description = "충전 금액", example = "50000")
        Long chargedAmount,

        @Schema(description = "충전 후 현재 잔액", example = "2050000")
        Long currentBalance,

        @Schema(description = "충전 일시", example = "2025-11-05T23:30:00")
        LocalDateTime chargedAt
) {
    public static ChargeBalanceResponse from(User user, Balance previousBalance, long chargedAmount) {
        return new ChargeBalanceResponse(
                user.getUserId(),
                previousBalance.amount(),
                chargedAmount,
                user.getBalance().amount(),
                LocalDateTime.now()
        );
    }
}
