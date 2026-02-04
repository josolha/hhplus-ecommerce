package com.sparta.ecommerce.infrastructure.config.security;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpMinutes,
        long refreshTokenExpDays
) {
}
