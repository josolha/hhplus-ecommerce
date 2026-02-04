package com.sparta.ecommerce.application.auth.usecase;

import com.sparta.ecommerce.application.auth.dto.RefreshAndAccessTokenResponse;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.infrastructure.config.security.JwtProvider;
import com.sparta.ecommerce.infrastructure.config.security.exception.InvalidTokenException;
import com.sparta.ecommerce.infrastructure.config.security.exception.LoggedOutTokenException;
import com.sparta.ecommerce.infrastructure.redis.RefreshTokenRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Refresh Token으로 Access Token 재발급
 * - JWT 검증 + Redis 화이트리스트 검증
 * - Refresh Token Rotation: 새 Refresh Token 발급 및 기존 토큰 무효화
 * - 무효화된 토큰 재사용 시도 시 탈취로 간주하여 모든 세션 종료
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshAccessTokenUseCase {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRedisService refreshTokenRedisService;
    private final UserRepository userRepository;

    public RefreshAndAccessTokenResponse execute(String refreshToken) {

        // 1) Refresh Token 자체 검증 (서명/만료/형식)
        if (!jwtProvider.validate(refreshToken)) {
            throw new InvalidTokenException("유효하지 않은 Refresh Token입니다");
        }

        // 2) userId 추출
        String userId = jwtProvider.getUserId(refreshToken);

        // 3) Redis 화이트리스트 검증 (로그아웃 확인 + 탈취 감지)
        if (!refreshTokenRedisService.validate(userId, refreshToken)) {
            // 무효화된 Refresh Token 재사용 시도 = 토큰 탈취 의심
            // 모든 세션 강제 종료 (보안 조치)
            log.warn("탈취 의심: 무효화된 Refresh Token 재사용 시도 - userId: {}", userId);
            refreshTokenRedisService.delete(userId);
            throw new LoggedOutTokenException("탈취 의심으로 모든 세션이 종료되었습니다. 다시 로그인해주세요.");
        }

        // 4) 사용자 조회 (role 필요)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("사용자를 찾을 수 없습니다"));

        // 5) 새 Access + Refresh Token 발급 (Rotation) ✅
        String newAccessToken = jwtProvider.createAccessToken(user.getUserId(), user.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getUserId());

        // 6) Redis에 새 Refresh Token 저장 (기존 토큰 무효화) ✅
        long ttl = jwtProvider.getRefreshTokenTtlSeconds();
        refreshTokenRedisService.save(user.getUserId(), newRefreshToken, ttl);

        log.info("토큰 재발급 성공 - userId: {}", userId);

        return RefreshAndAccessTokenResponse.of(newAccessToken, newRefreshToken);
    }
}
