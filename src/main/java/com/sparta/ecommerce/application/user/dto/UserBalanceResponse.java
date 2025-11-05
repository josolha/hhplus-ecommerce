package com.sparta.ecommerce.application.user.dto;

import com.sparta.ecommerce.domain.user.User;

public record UserBalanceResponse(
        String userId,
        Long balance
) {
    public static UserBalanceResponse from(User user) {
        return new UserBalanceResponse(
                user.getUserId(),
                user.getBalance().amount()
        );
    }
}
