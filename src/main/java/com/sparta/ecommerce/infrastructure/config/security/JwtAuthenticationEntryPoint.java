package com.sparta.ecommerce.infrastructure.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ecommerce.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * JWT 인증 실패 시 처리 핸들러 (401 Unauthorized)
 * - 토큰이 없거나 유효하지 않을 때 실행
 * - GlobalExceptionHandler의 AUTH* 에러 코드와 일관성 유지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Filter에서 전달한 코드 추출 (기본값: "INVALID_TOKEN")
        String errorType = authException.getMessage();

        // 에러 타입에 따른 ErrorCode 매핑
        ErrorCode errorCode = switch (errorType) {
            case "INVALID_TOKEN" -> ErrorCode.AUTH001;
            case "EXPIRED_TOKEN" -> ErrorCode.AUTH002;
            default -> ErrorCode.AUTH001;
        };

        log.warn("JWT 인증 실패: code={}, message={}, uri={}",
                errorCode.getCode(), errorCode.getMessage(), request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), Map.of(
                "code", errorCode.getCode(),
                "message", errorCode.getMessage()
        ));
    }
}