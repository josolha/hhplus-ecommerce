package com.sparta.ecommerce.application.auth.dto;

/**
 * Refresh Token으로 토큰 재발급 응답
 * - 새 Access Token과 새 Refresh Token 반환 (Rotation 방식)
 */
public record RefreshAndAccessTokenResponse(
        String accessToken,
        String refreshToken
) {
    public static RefreshAndAccessTokenResponse of(String accessToken, String refreshToken) {
        return new RefreshAndAccessTokenResponse(accessToken, refreshToken);
    }
}
