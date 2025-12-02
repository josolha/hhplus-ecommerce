package com.sparta.ecommerce.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 캐시 설정
 *
 * Cache-Aside 전략:
 * 1. 애플리케이션이 캐시 먼저 확인
 * 2. 캐시 미스 시 DB 조회 후 캐시 저장
 * 3. TTL 만료 시 자동 삭제
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 캐시 이름 상수
     * cache: prefix로 분산 락과 명확히 구분
     */
    public static final String POPULAR_PRODUCTS = "cache:popularProducts";
    public static final String PRODUCT_DETAIL = "cache:productDetail";
    public static final String PRODUCT_LIST = "cache:productList";

    /**
     * Redis 캐시 매니저 설정
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))  // 기본 TTL 10분
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper())
                        )
                );

        // 캐시별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 인기 상품: 30분 TTL
        cacheConfigurations.put(POPULAR_PRODUCTS,
                defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 상품 상세: 10분 TTL
        cacheConfigurations.put(PRODUCT_DETAIL,
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // 상품 목록: 10분 TTL
        cacheConfigurations.put(PRODUCT_LIST,
                defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * ObjectMapper 설정
     * LocalDateTime 직렬화 지원
     * Record 타입 역직렬화 지원
     */
    private ObjectMapper objectMapper() {
        // 타입 검증 설정 (보안 + Record 지원)
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 타입 정보를 포함하여 직렬화 (Record 역직렬화 지원)
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

        return mapper;
    }
}
