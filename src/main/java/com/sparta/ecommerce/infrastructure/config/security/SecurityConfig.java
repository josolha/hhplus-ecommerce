package com.sparta.ecommerce.infrastructure.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // REST API 서버 기본값: 세션/폼로그인 사용 안 함
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            // 세션 안 씀 (JWT 전제)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 디폴트 로그인 페이지(/login) 끄기
            .formLogin(form -> form.disable())
            // Basic Auth 팝업 끄기
            .httpBasic(basic -> basic.disable())

            // URL별 접근 제어
            .authorizeHttpRequests(auth -> auth
                    // --- 공개(인증 없이 접근) ---
                    .requestMatchers(
                            "/",
                            "/error",
                            "/favicon.ico"
                    ).permitAll()

                    // 로그인/회원가입 같은 auth API는 일단 열어둠
                    .requestMatchers("/auth/**").permitAll()
                    // Swagger 쓰면 아래 주석 해제
                    // .requestMatchers(
                    //     "/swagger-ui/**",
                    //     "/v3/api-docs/**"
                    // ).permitAll()
                    // 상품 조회는 공개로 두고 싶으면(선택)
                    .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                    // --- 그 외는 전부 인증 필요 ---
                    .anyRequest().authenticated()
                );

        return http.build();
    }
}
