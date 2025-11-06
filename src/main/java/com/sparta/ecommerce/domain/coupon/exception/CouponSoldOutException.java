package com.sparta.ecommerce.domain.coupon.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 쿠폰이 모두 소진되었을 때 발생하는 예외
 */
public class CouponSoldOutException extends BusinessException {
    public CouponSoldOutException() {
        super(ErrorCode.C001);
    }

    public CouponSoldOutException(String message) {
        super(ErrorCode.C001, message);
    }
}
