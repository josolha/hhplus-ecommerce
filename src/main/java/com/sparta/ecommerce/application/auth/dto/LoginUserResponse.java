package com.sparta.ecommerce.application.auth.dto;

public record LoginUserResponse(
        String message,
        String accessToken,
        String refreshToken
) {
    public static LoginUserResponse success(String accessToken, String refreshToken) {
        return new LoginUserResponse("로그인 성공", accessToken, refreshToken);
    }

    public static LoginUserResponse refreshed(String accessToken, String refreshToken) {
        return new LoginUserResponse("토큰 재발급 성공", accessToken, refreshToken);
    }
}
