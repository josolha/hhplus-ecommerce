package com.sparta.ecommerce.application.auth.usecase;

import com.sparta.ecommerce.infrastructure.redis.RefreshTokenRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 로그아웃 처리
 * - Redis에서 Refresh Token 삭제
 * - 이후 해당 Refresh Token으로 재발급 시도 시 실패
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogoutUserUseCase {

    private final RefreshTokenRedisService refreshTokenRedisService;

    /**
     * 로그아웃 실행
     * @param userId 로그아웃할 사용자 ID
     */
    public void execute(String userId) {
        // Redis에서 Refresh Token 삭제
        refreshTokenRedisService.delete(userId);

        log.info("로그아웃 완료 - userId: {}", userId);
    }
}
