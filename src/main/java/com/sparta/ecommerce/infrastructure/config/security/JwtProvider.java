package com.sparta.ecommerce.infrastructure.config.security;

import com.sparta.ecommerce.domain.user.entity.Role;
import com.sparta.ecommerce.infrastructure.config.security.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
@Slf4j
public class JwtProvider {

    private final JwtProperties properties;
    private SecretKey secretKey;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.secretKey = Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8)
        );
    }

    /* =====================
       Token Create
       ===================== */

    public String createAccessToken(String userId, Role role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.accessTokenExpMinutes() * 60);

        return Jwts.builder()
                .setSubject(userId)                 // userId
                .claim("role", role.name())         // ROLE 정보
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(String userId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.refreshTokenExpDays() * 24 * 60 * 60);

        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /* =====================
       TTL Getter (이게 핵심)
       ===================== */

    public long getRefreshTokenTtlSeconds() {
        return properties.refreshTokenExpDays() * 24 * 60 * 60;
    }

    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenExpMinutes() * 60;
    }

    /* =====================
       Token Validate
       ===================== */

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.info("JWT expired");
        } catch (SignatureException e) { // 서명 불일치
            log.info("JWT invalid signature");
        } catch (SecurityException | MalformedJwtException e) {
            log.info("JWT invalid signature or malformed");
        } catch (UnsupportedJwtException e) {
            log.info("JWT unsupported");
        } catch (IllegalArgumentException e) {
            log.info("JWT empty/invalid");
        }
        return false;
    }

    /* =====================
       Claims Extract
       ===================== */

    public String getUserId(String token) {
        return parseClaims(token)
                .getBody()
                .getSubject();
    }

    public Role getRole(String token) {
        String role = parseClaims(token).getBody().get("role", String.class);

        if (role == null || role.isBlank()) {
            // access 토큰이 아니거나, 잘못된 토큰(클레임 누락)
            throw new JwtException("Missing role claim");
        }

        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            // role 문자열이 enum에 없음
            throw new JwtException("Invalid role claim: " + role, e);
        }
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
    }


}