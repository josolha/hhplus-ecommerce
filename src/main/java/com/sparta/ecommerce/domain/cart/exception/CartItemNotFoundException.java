package com.sparta.ecommerce.domain.cart.exception;

import com.sparta.ecommerce.common.exception.BusinessException;
import com.sparta.ecommerce.common.exception.ErrorCode;

/**
 * 장바구니 항목을 찾을 수 없을 때 발생하는 예외
 */
public class CartItemNotFoundException extends BusinessException {
    public CartItemNotFoundException(String cartItemId) {
        super(ErrorCode.COMMON002, "장바구니 항목을 찾을 수 없습니다: " + cartItemId);
    }
}
