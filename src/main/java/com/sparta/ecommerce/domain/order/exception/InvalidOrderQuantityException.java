package com.sparta.ecommerce.domain.order.exception;

import com.sparta.ecommerce.common.exception.BusinessException;
import com.sparta.ecommerce.common.exception.ErrorCode;

/**
 * 유효하지 않은 주문 수량일 때 발생하는 예외
 */
public class InvalidOrderQuantityException extends BusinessException {
    public InvalidOrderQuantityException() {
        super(ErrorCode.O001);
    }

    public InvalidOrderQuantityException(int quantity) {
        super(ErrorCode.O001, "유효하지 않은 주문 수량입니다: " + quantity);
    }
}
