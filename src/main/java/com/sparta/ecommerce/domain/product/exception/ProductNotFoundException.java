package com.sparta.ecommerce.domain.product.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 상품을 찾을 수 없을 때 발생하는 예외
 */
public class ProductNotFoundException extends BusinessException {
    public ProductNotFoundException() {
        super(ErrorCode.P001);
    }

    public ProductNotFoundException(String productId) {
        super(ErrorCode.P001, "상품을 찾을 수 없습니다: " + productId);
    }
}
