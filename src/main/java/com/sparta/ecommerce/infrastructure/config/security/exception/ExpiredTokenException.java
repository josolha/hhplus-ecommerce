package com.sparta.ecommerce.infrastructure.config.security.exception;

import com.sparta.ecommerce.common.exception.BusinessException;
import com.sparta.ecommerce.common.exception.ErrorCode;

/**
 * 만료된 토큰 예외
 */
public class ExpiredTokenException extends BusinessException {

    public ExpiredTokenException() {
        super(ErrorCode.AUTH002);
    }

    public ExpiredTokenException(String message) {
        super(ErrorCode.AUTH002, message);
    }
}
