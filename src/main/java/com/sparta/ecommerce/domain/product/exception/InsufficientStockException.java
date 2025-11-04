package com.sparta.ecommerce.domain.product.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 재고가 부족할 때 발생하는 예외
 */
public class InsufficientStockException extends BusinessException {
    public InsufficientStockException() {
        super(ErrorCode.P002);
    }

    public InsufficientStockException(int requested, int available) {
        super(ErrorCode.P002,
              String.format("재고가 부족합니다. 요청 수량: %d, 재고: %d", requested, available));
    }
}
