package com.sparta.ecommerce.application.auth.usecase;

import com.sparta.ecommerce.application.auth.dto.LoginUserRequest;
import com.sparta.ecommerce.application.auth.dto.LoginUserResponse;
import com.sparta.ecommerce.domain.user.entity.Provider;
import com.sparta.ecommerce.domain.user.entity.Role;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import com.sparta.ecommerce.infrastructure.config.security.JwtProvider;
import com.sparta.ecommerce.infrastructure.redis.RefreshTokenRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUserUseCase 단위 테스트")
class LoginUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenRedisService refreshTokenRedisService;

    @InjectMocks
    private LoginUserUseCase loginUserUseCase;

    private User testUser;
    private String rawPassword = "password123";
    private String encodedPassword = "$2a$10$encoded.password.hash";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId("user123")
                .email("test@example.com")
                .password(encodedPassword)
                .name("홍길동")
                .phoneNumber("010-1234-5678")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .balance(Balance.zero())
                .build();
    }

    @Test
    @DisplayName("올바른 이메일과 비밀번호로 로그인하면 토큰을 반환한다")
    void 로그인_성공() {
        // given
        LoginUserRequest request = new LoginUserRequest("test@example.com", rawPassword);
        String accessToken = "access.token.value";
        String refreshToken = "refresh.token.value";
        long ttl = 604800L; // 7일

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);
        given(jwtProvider.createAccessToken(testUser.getUserId(), testUser.getRole())).willReturn(accessToken);
        given(jwtProvider.createRefreshToken(testUser.getUserId())).willReturn(refreshToken);
        given(jwtProvider.getRefreshTokenTtlSeconds()).willReturn(ttl);

        // when
        LoginUserResponse response = loginUserUseCase.execute(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(accessToken);
        assertThat(response.refreshToken()).isEqualTo(refreshToken);

        verify(userRepository).findByEmail(request.email());
        verify(passwordEncoder).matches(rawPassword, encodedPassword);
        verify(jwtProvider).createAccessToken(testUser.getUserId(), testUser.getRole());
        verify(jwtProvider).createRefreshToken(testUser.getUserId());
        verify(refreshTokenRedisService).save(testUser.getUserId(), refreshToken, ttl);
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시도하면 예외가 발생한다")
    void 로그인_실패_존재하지_않는_이메일() {
        // given
        LoginUserRequest request = new LoginUserRequest("nonexistent@example.com", rawPassword);
        given(userRepository.findByEmail(request.email())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> loginUserUseCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");

        verify(userRepository).findByEmail(request.email());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtProvider, never()).createAccessToken(anyString(), any(Role.class));
        verify(refreshTokenRedisService, never()).save(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시도하면 예외가 발생한다")
    void 로그인_실패_잘못된_비밀번호() {
        // given
        LoginUserRequest request = new LoginUserRequest("test@example.com", "wrongPassword");
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(testUser));
        given(passwordEncoder.matches("wrongPassword", encodedPassword)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> loginUserUseCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");

        verify(userRepository).findByEmail(request.email());
        verify(passwordEncoder).matches("wrongPassword", encodedPassword);
        verify(jwtProvider, never()).createAccessToken(anyString(), any(Role.class));
        verify(refreshTokenRedisService, never()).save(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("소셜 로그인 계정으로 일반 로그인 시도하면 예외가 발생한다")
    void 로그인_실패_소셜_계정() {
        // given
        User kakaoUser = User.builder()
                .userId("user123")
                .email("test@example.com")
                .password(encodedPassword) // 소셜 로그인도 password가 있을 수 있음
                .name("홍길동")
                .phoneNumber("010-1234-5678")
                .provider(Provider.KAKAO)
                .role(Role.USER)
                .balance(Balance.zero())
                .build();

        LoginUserRequest request = new LoginUserRequest("test@example.com", rawPassword);
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(kakaoUser));
        given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> loginUserUseCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("소셜 로그인 계정입니다.");

        verify(userRepository).findByEmail(request.email());
        verify(passwordEncoder).matches(rawPassword, encodedPassword);
        verify(jwtProvider, never()).createAccessToken(anyString(), any(Role.class));
        verify(refreshTokenRedisService, never()).save(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("관리자 권한을 가진 사용자도 로그인할 수 있다")
    void 로그인_성공_관리자() {
        // given
        User adminUser = User.builder()
                .userId("admin123")
                .email("admin@example.com")
                .password(encodedPassword)
                .name("관리자")
                .phoneNumber("010-9999-9999")
                .provider(Provider.LOCAL)
                .role(Role.ADMIN)
                .balance(Balance.zero())
                .build();

        LoginUserRequest request = new LoginUserRequest("admin@example.com", rawPassword);
        String accessToken = "admin.access.token";
        String refreshToken = "admin.refresh.token";
        long ttl = 604800L;

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(adminUser));
        given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);
        given(jwtProvider.createAccessToken(adminUser.getUserId(), adminUser.getRole())).willReturn(accessToken);
        given(jwtProvider.createRefreshToken(adminUser.getUserId())).willReturn(refreshToken);
        given(jwtProvider.getRefreshTokenTtlSeconds()).willReturn(ttl);

        // when
        LoginUserResponse response = loginUserUseCase.execute(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo(accessToken);
        assertThat(response.refreshToken()).isEqualTo(refreshToken);

        verify(refreshTokenRedisService).save(adminUser.getUserId(), refreshToken, ttl);
    }
}
