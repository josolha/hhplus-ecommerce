package com.sparta.ecommerce.infrastructure.config.security;

import com.sparta.ecommerce.domain.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtProvider 단위 테스트")
class JwtProviderTest {

    private JwtProvider jwtProvider;
    private JwtProperties properties;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        // 테스트용 JWT 설정
        properties = new JwtProperties(
                "test-secret-key-for-jwt-signing-minimum-256-bits-required-here",
                30L, // 30분
                7L    // 7일
        );

        jwtProvider = new JwtProvider(properties);

        // SecretKey 초기화 (PostConstruct 대신)
        secretKey = Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8)
        );
        ReflectionTestUtils.setField(jwtProvider, "secretKey", secretKey);
    }

    @Test
    @DisplayName("Access Token 생성 시 userId와 role이 포함된다")
    void createAccessToken_포함_userId_role() {
        // given
        String userId = "user123";
        Role role = Role.USER;

        // when
        String token = jwtProvider.createAccessToken(userId, role);

        // then
        assertThat(token).isNotNull();

        // 토큰 파싱하여 내용 검증
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(claims.get("role", String.class)).isEqualTo(role.name());
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("Refresh Token 생성 시 userId만 포함된다")
    void createRefreshToken_포함_userId_only() {
        // given
        String userId = "user123";

        // when
        String token = jwtProvider.createRefreshToken(userId);

        // then
        assertThat(token).isNotNull();

        // 토큰 파싱하여 내용 검증
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(claims.get("role")).isNull(); // role은 없어야 함
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("유효한 토큰에 대해 validate는 true를 반환한다")
    void validate_유효한_토큰() {
        // given
        String token = jwtProvider.createAccessToken("user123", Role.USER);

        // when
        boolean isValid = jwtProvider.validate(token);

        // then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("잘못된 서명의 토큰에 대해 validate는 false를 반환한다")
    void validate_잘못된_서명() {
        // given
        String token = jwtProvider.createAccessToken("user123", Role.USER);
        String tamperedToken = token.substring(0, token.length() - 10) + "tampered00";

        // when
        boolean isValid = jwtProvider.validate(tamperedToken);

        // then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("malformed 토큰에 대해 validate는 false를 반환한다")
    void validate_malformed_토큰() {
        // given
        String malformedToken = "invalid.token.format";

        // when
        boolean isValid = jwtProvider.validate(malformedToken);

        // then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("getUserId는 토큰에서 userId를 추출한다")
    void getUserId_추출_성공() {
        // given
        String userId = "user123";
        String token = jwtProvider.createAccessToken(userId, Role.USER);

        // when
        String extractedUserId = jwtProvider.getUserId(token);

        // then
        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    @DisplayName("getRole은 토큰에서 role을 추출한다")
    void getRole_추출_성공() {
        // given
        String userId = "user123";
        Role role = Role.ADMIN;
        String token = jwtProvider.createAccessToken(userId, role);

        // when
        Role extractedRole = jwtProvider.getRole(token);

        // then
        assertThat(extractedRole).isEqualTo(role);
    }

    @Test
    @DisplayName("role이 없는 토큰(Refresh Token)에서 getRole 호출 시 예외 발생")
    void getRole_role_없음_예외발생() {
        // given
        String token = jwtProvider.createRefreshToken("user123");

        // when & then
        assertThatThrownBy(() -> jwtProvider.getRole(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class)
                .hasMessageContaining("Missing role claim");
    }

    @Test
    @DisplayName("getRefreshTokenTtlSeconds는 올바른 TTL을 반환한다")
    void getRefreshTokenTtlSeconds_정상_반환() {
        // when
        long ttl = jwtProvider.getRefreshTokenTtlSeconds();

        // then
        assertThat(ttl).isEqualTo(7L * 24 * 60 * 60); // 7일을 초로 변환
    }

    @Test
    @DisplayName("getAccessTokenTtlSeconds는 올바른 TTL을 반환한다")
    void getAccessTokenTtlSeconds_정상_반환() {
        // when
        long ttl = jwtProvider.getAccessTokenTtlSeconds();

        // then
        assertThat(ttl).isEqualTo(30L * 60); // 30분을 초로 변환
    }
}
