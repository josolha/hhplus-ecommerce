package com.sparta.ecommerce.domain.coupon.exception;


import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 이미 사용된 쿠폰일 때 발생하는 예외
 */
public class CouponAlreadyUsedException extends BusinessException {
    public CouponAlreadyUsedException() {
        super(ErrorCode.C004);
    }

    public CouponAlreadyUsedException(String couponId) {
        super(ErrorCode.C004, "이미 사용된 쿠폰입니다: " + couponId);
    }
}
