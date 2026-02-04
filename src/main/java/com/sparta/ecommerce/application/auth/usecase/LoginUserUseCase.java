package com.sparta.ecommerce.application.auth.usecase;


import com.sparta.ecommerce.application.auth.dto.LoginUserRequest;
import com.sparta.ecommerce.application.auth.dto.LoginUserResponse;
import com.sparta.ecommerce.domain.user.entity.Provider;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.infrastructure.config.security.JwtProvider;
import com.sparta.ecommerce.infrastructure.redis.RefreshTokenRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRedisService refreshTokenRedisService;

    public LoginUserResponse execute(LoginUserRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (user.getProvider() != null && user.getProvider() != Provider.LOCAL) {
            throw new IllegalArgumentException("소셜 로그인 계정입니다.");
        }
        String accessToken = jwtProvider.createAccessToken(
                user.getUserId(), user.getRole()
        );
        String refreshToken = jwtProvider.createRefreshToken(
                user.getUserId()
        );
        long ttlSeconds = jwtProvider.getRefreshTokenTtlSeconds();

        refreshTokenRedisService.save(user.getUserId(), refreshToken, ttlSeconds);

        return LoginUserResponse.success(accessToken, refreshToken);
    }

}
