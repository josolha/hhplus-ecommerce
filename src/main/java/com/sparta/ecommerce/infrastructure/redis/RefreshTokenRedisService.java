package com.sparta.ecommerce.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Refresh Token Redis 저장/조회/삭제 서비스
 * - 화이트리스트 방식으로 Refresh Token 관리
 * - 로그아웃/강제 만료 지원
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenRedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "refresh_token:";

    /**
     * Refresh Token 저장
     * @param userId 사용자 ID
     * @param refreshToken 토큰 값
     * @param ttlSeconds 만료 시간 (초)
     */
    public void save(String userId, String refreshToken, long ttlSeconds) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Refresh Token 조회
     * @param userId 사용자 ID
     * @return Refresh Token 또는 null
     */
    public String get(String userId) {
        String key = KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Refresh Token 존재 여부 확인
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    public boolean exists(String userId) {
        String key = KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Refresh Token 검증 (저장된 토큰과 비교)
     * @param userId 사용자 ID
     * @param refreshToken 검증할 토큰
     * @return 유효 여부
     */
    public boolean validate(String userId, String refreshToken) {
        String storedToken = get(userId);
        return storedToken != null && storedToken.equals(refreshToken);
    }

    /**
     * Refresh Token 삭제 (로그아웃)
     * @param userId 사용자 ID
     */
    public void delete(String userId) {
        String key = KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}
