package com.sparta.ecommerce.domain.coupon.exception;

import com.sparta.ecommerce.common.exception.BusinessException;
import com.sparta.ecommerce.common.exception.ErrorCode;

/**
 * 유효하지 않은 쿠폰일 때 발생하는 예외
 */
public class InvalidCouponException extends BusinessException {
    public InvalidCouponException() {
        super(ErrorCode.C002);
    }

    public InvalidCouponException(String message) {
        super(ErrorCode.C002, message);
    }
}
