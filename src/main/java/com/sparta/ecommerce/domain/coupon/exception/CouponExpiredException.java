package com.sparta.ecommerce.domain.coupon.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 만료된 쿠폰일 때 발생하는 예외
 */
public class CouponExpiredException extends BusinessException {
    public CouponExpiredException() {
        super(ErrorCode.C003);
    }

    public CouponExpiredException(String couponId) {
        super(ErrorCode.C003, "만료된 쿠폰입니다: " + couponId);
    }
}
