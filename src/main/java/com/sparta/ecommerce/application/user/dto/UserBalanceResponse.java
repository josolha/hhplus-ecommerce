package com.sparta.ecommerce.application.user.dto;

import com.sparta.ecommerce.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserBalanceResponse(
        @Schema(description = "사용자 ID", example = "U001")
        String userId,

        @Schema(description = "현재 잔액", example = "2000000")
        Long balance
) {
    public static UserBalanceResponse from(User user) {
        return new UserBalanceResponse(
                user.getUserId(),
                user.getBalance().amount()
        );
    }
}
