package com.sparta.ecommerce.infrastructure.config.security.exception;

import com.sparta.ecommerce.common.exception.BusinessException;
import com.sparta.ecommerce.common.exception.ErrorCode;

/**
 * 유효하지 않은 토큰 예외
 * - 토큰 형식 오류, 서명 불일치 등
 */
public class InvalidTokenException extends BusinessException {

    public InvalidTokenException() {
        super(ErrorCode.AUTH001);
    }

    public InvalidTokenException(String message) {
        super(ErrorCode.AUTH001, message);
    }
}
