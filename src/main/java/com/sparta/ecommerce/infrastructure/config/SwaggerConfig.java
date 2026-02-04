package com.sparta.ecommerce.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 설정
 * - JWT 인증 포함
 * - API 문서 자동 생성
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // JWT 보안 스키마 정의
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        // 전역 보안 요구사항
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme))
                .addSecurityItem(securityRequirement)
                .info(apiInfo())
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("https://api.example.com")
                                .description("운영 서버")
                ));
    }

    private Info apiInfo() {
        return new Info()
                .title("E-commerce Core API")
                .description("""
                        이커머스 핵심 시스템 API 문서

                        ## 주요 기능
                        - 회원 인증/인가 (JWT)
                        - 상품 관리 및 인기 상품 조회
                        - 장바구니 및 주문/결제
                        - 쿠폰 발급 및 사용
                        - 잔액 충전 및 조회

                        ## 인증 방법
                        1. `/api/auth/login`으로 로그인하여 Access Token 발급
                        2. 우측 상단 'Authorize' 버튼 클릭
                        3. `Bearer {accessToken}` 형식으로 입력 (Bearer는 자동 추가됨)
                        4. 인증이 필요한 API 호출 시 자동으로 헤더에 포함

                        ## Access Token 만료 시
                        - `/api/auth/refresh`로 Refresh Token을 사용하여 새 Access Token 발급
                        """)
                .version("v1.0.0")
                .contact(new io.swagger.v3.oas.models.info.Contact()
                        .name("E-commerce Team")
                        .email("support@example.com")
                        .url("https://github.com/your-repo"));
    }
}
