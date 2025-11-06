package com.sparta.ecommerce.domain.cart.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 장바구니를 찾을 수 없을 때 발생하는 예외
 */
public class CartNotFoundException extends BusinessException {
    public CartNotFoundException(String userId) {
        super(ErrorCode.COMMON002, "장바구니를 찾을 수 없습니다: " + userId);
    }
}
