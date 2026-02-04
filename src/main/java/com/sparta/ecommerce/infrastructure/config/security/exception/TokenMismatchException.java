package com.sparta.ecommerce.infrastructure.config.security.exception;

import com.sparta.ecommerce.common.exception.BusinessException;
import com.sparta.ecommerce.common.exception.ErrorCode;

/**
 * 토큰 불일치 예외
 * - Redis에 저장된 Refresh Token과 요청 토큰이 다를 때
 */
public class TokenMismatchException extends BusinessException {

    public TokenMismatchException() {
        super(ErrorCode.AUTH004);
    }

    public TokenMismatchException(String message) {
        super(ErrorCode.AUTH004, message);
    }
}
