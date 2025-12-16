package com.sparta.ecommerce.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 설정
 * - StringRedisTemplate: Sorted Set 랭킹 시스템 + Blocking Queue
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate 설정
     * - Sorted Set 랭킹용
     * - Blocking Queue (BRPOP) 지원
     * - 단순 String 직렬화만 사용
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
