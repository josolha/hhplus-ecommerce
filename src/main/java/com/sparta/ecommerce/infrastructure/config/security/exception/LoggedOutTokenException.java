package com.sparta.ecommerce.infrastructure.config.security.exception;

import com.sparta.ecommerce.common.exception.BusinessException;
import com.sparta.ecommerce.common.exception.ErrorCode;

/**
 * 로그아웃된 토큰 예외
 * - Redis에 저장되지 않은 Refresh Token (로그아웃됨)
 */
public class LoggedOutTokenException extends BusinessException {

    public LoggedOutTokenException() {
        super(ErrorCode.AUTH003);
    }

    public LoggedOutTokenException(String message) {
        super(ErrorCode.AUTH003, message);
    }
}
