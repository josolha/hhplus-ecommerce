package com.sparta.ecommerce.domain.order.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 주문을 찾을 수 없을 때 발생하는 예외
 */
public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException() {
        super(ErrorCode.O002);
    }

    public OrderNotFoundException(String orderId) {
        super(ErrorCode.O002, "주문을 찾을 수 없습니다: " + orderId);
    }
}
