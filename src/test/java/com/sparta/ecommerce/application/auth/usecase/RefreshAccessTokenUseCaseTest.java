package com.sparta.ecommerce.application.auth.usecase;

import com.sparta.ecommerce.application.auth.dto.RefreshAndAccessTokenResponse;
import com.sparta.ecommerce.domain.user.entity.Provider;
import com.sparta.ecommerce.domain.user.entity.Role;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import com.sparta.ecommerce.infrastructure.config.security.JwtProvider;
import com.sparta.ecommerce.infrastructure.config.security.exception.InvalidTokenException;
import com.sparta.ecommerce.infrastructure.config.security.exception.LoggedOutTokenException;
import com.sparta.ecommerce.infrastructure.redis.RefreshTokenRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshAccessTokenUseCase 단위 테스트")
class RefreshAccessTokenUseCaseTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenRedisService refreshTokenRedisService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshAccessTokenUseCase refreshAccessTokenUseCase;

    private User testUser;
    private String validRefreshToken = "valid.refresh.token";
    private String userId = "user123";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(userId)
                .email("test@example.com")
                .password("encoded.password")
                .name("홍길동")
                .phoneNumber("010-1234-5678")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .balance(Balance.zero())
                .build();
    }

    @Test
    @DisplayName("유효한 Refresh Token으로 Access Token과 새 Refresh Token을 발급받는다")
    void 토큰_재발급_성공() {
        // given
        String newAccessToken = "new.access.token";
        String newRefreshToken = "new.refresh.token";
        long ttl = 604800L;

        given(jwtProvider.validate(validRefreshToken)).willReturn(true);
        given(jwtProvider.getUserId(validRefreshToken)).willReturn(userId);
        given(refreshTokenRedisService.validate(userId, validRefreshToken)).willReturn(true);
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(jwtProvider.createAccessToken(userId, testUser.getRole())).willReturn(newAccessToken);
        given(jwtProvider.createRefreshToken(userId)).willReturn(newRefreshToken);
        given(jwtProvider.getRefreshTokenTtlSeconds()).willReturn(ttl);

        // when
        RefreshAndAccessTokenResponse response = refreshAccessTokenUseCase.execute(validRefreshToken);

        // then
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(newAccessToken);
        assertThat(response.refreshToken()).isEqualTo(newRefreshToken);

        verify(jwtProvider).validate(validRefreshToken);
        verify(jwtProvider).getUserId(validRefreshToken);
        verify(refreshTokenRedisService).validate(userId, validRefreshToken);
        verify(userRepository).findById(userId);
        verify(jwtProvider).createAccessToken(userId, testUser.getRole());
        verify(jwtProvider).createRefreshToken(userId);
        verify(refreshTokenRedisService).save(userId, newRefreshToken, ttl);
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 재발급 시도하면 예외가 발생한다")
    void 토큰_재발급_실패_유효하지_않은_토큰() {
        // given
        String invalidToken = "invalid.token";
        given(jwtProvider.validate(invalidToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> refreshAccessTokenUseCase.execute(invalidToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("유효하지 않은 Refresh Token입니다");

        verify(jwtProvider).validate(invalidToken);
        verify(jwtProvider, never()).getUserId(anyString());
        verify(refreshTokenRedisService, never()).validate(anyString(), anyString());
    }

    @Test
    @DisplayName("Redis에 없는(로그아웃된) Refresh Token으로 재발급 시도하면 탈취 의심으로 간주한다")
    void 토큰_재발급_실패_로그아웃된_토큰_탈취의심() {
        // given
        given(jwtProvider.validate(validRefreshToken)).willReturn(true);
        given(jwtProvider.getUserId(validRefreshToken)).willReturn(userId);
        given(refreshTokenRedisService.validate(userId, validRefreshToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> refreshAccessTokenUseCase.execute(validRefreshToken))
                .isInstanceOf(LoggedOutTokenException.class)
                .hasMessage("탈취 의심으로 모든 세션이 종료되었습니다. 다시 로그인해주세요.");

        verify(jwtProvider).validate(validRefreshToken);
        verify(jwtProvider).getUserId(validRefreshToken);
        verify(refreshTokenRedisService).validate(userId, validRefreshToken);
        verify(refreshTokenRedisService).delete(userId); // 모든 세션 강제 종료
        verify(userRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 토큰으로 재발급 시도하면 예외가 발생한다")
    void 토큰_재발급_실패_사용자_없음() {
        // given
        given(jwtProvider.validate(validRefreshToken)).willReturn(true);
        given(jwtProvider.getUserId(validRefreshToken)).willReturn(userId);
        given(refreshTokenRedisService.validate(userId, validRefreshToken)).willReturn(true);
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refreshAccessTokenUseCase.execute(validRefreshToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("사용자를 찾을 수 없습니다");

        verify(jwtProvider).validate(validRefreshToken);
        verify(jwtProvider).getUserId(validRefreshToken);
        verify(refreshTokenRedisService).validate(userId, validRefreshToken);
        verify(userRepository).findById(userId);
        verify(jwtProvider, never()).createAccessToken(anyString(), any(Role.class));
    }

    @Test
    @DisplayName("Refresh Token Rotation이 정상적으로 작동한다 - 기존 토큰은 무효화되고 새 토큰이 저장된다")
    void 토큰_로테이션_정상_작동() {
        // given
        String newAccessToken = "new.access.token";
        String newRefreshToken = "new.refresh.token";
        long ttl = 604800L;

        given(jwtProvider.validate(validRefreshToken)).willReturn(true);
        given(jwtProvider.getUserId(validRefreshToken)).willReturn(userId);
        given(refreshTokenRedisService.validate(userId, validRefreshToken)).willReturn(true);
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(jwtProvider.createAccessToken(userId, testUser.getRole())).willReturn(newAccessToken);
        given(jwtProvider.createRefreshToken(userId)).willReturn(newRefreshToken);
        given(jwtProvider.getRefreshTokenTtlSeconds()).willReturn(ttl);

        // when
        RefreshAndAccessTokenResponse response = refreshAccessTokenUseCase.execute(validRefreshToken);

        // then
        assertThat(response.refreshToken()).isNotEqualTo(validRefreshToken);
        assertThat(response.refreshToken()).isEqualTo(newRefreshToken);

        // 새 Refresh Token이 Redis에 저장되어 기존 토큰 무효화
        verify(refreshTokenRedisService).save(userId, newRefreshToken, ttl);
    }

    @Test
    @DisplayName("관리자 권한을 가진 사용자도 토큰 재발급이 가능하다")
    void 토큰_재발급_성공_관리자() {
        // given
        User adminUser = User.builder()
                .userId(userId)
                .email("admin@example.com")
                .password("encoded.password")
                .name("관리자")
                .phoneNumber("010-9999-9999")
                .provider(Provider.LOCAL)
                .role(Role.ADMIN)
                .balance(Balance.zero())
                .build();

        String newAccessToken = "admin.new.access.token";
        String newRefreshToken = "admin.new.refresh.token";
        long ttl = 604800L;

        given(jwtProvider.validate(validRefreshToken)).willReturn(true);
        given(jwtProvider.getUserId(validRefreshToken)).willReturn(userId);
        given(refreshTokenRedisService.validate(userId, validRefreshToken)).willReturn(true);
        given(userRepository.findById(userId)).willReturn(Optional.of(adminUser));
        given(jwtProvider.createAccessToken(userId, adminUser.getRole())).willReturn(newAccessToken);
        given(jwtProvider.createRefreshToken(userId)).willReturn(newRefreshToken);
        given(jwtProvider.getRefreshTokenTtlSeconds()).willReturn(ttl);

        // when
        RefreshAndAccessTokenResponse response = refreshAccessTokenUseCase.execute(validRefreshToken);

        // then
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(newAccessToken);
        assertThat(response.refreshToken()).isEqualTo(newRefreshToken);

        verify(jwtProvider).createAccessToken(userId, Role.ADMIN);
    }
}
