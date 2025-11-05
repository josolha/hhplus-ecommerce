package com.sparta.ecommerce.application.user.dto;

import com.sparta.ecommerce.domain.user.User;
import com.sparta.ecommerce.domain.user.vo.Balance;
import java.time.LocalDateTime;

/**
 * 잔액 충전 응답 DTO
 */
public record ChargeBalanceResponse(
        String userId,
        Long previousBalance,
        Long chargedAmount,
        Long currentBalance,
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
