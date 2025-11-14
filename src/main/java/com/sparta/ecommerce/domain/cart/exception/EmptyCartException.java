package com.sparta.ecommerce.domain.cart.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 장바구니가 비어있을 때 발생하는 예외
 */
public class EmptyCartException extends BusinessException {
    public EmptyCartException(String message) {
        super(ErrorCode.COMMON001, message);
    }
}
