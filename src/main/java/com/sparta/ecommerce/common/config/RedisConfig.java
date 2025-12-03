package com.sparta.ecommerce.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 설정
 * Sorted Set 기반 랭킹 시스템을 위한 StringRedisTemplate 설정
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate 설정 (Sorted Set 랭킹용)
     * - 단순 String 직렬화만 사용
     * - Sorted Set, Set, List 등 기본 자료구조에 최적화
     * - JSON 직렬화 오버헤드 없음
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
