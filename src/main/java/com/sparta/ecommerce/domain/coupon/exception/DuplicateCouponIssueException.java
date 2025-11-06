package com.sparta.ecommerce.domain.coupon.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 이미 발급받은 쿠폰을 중복 발급하려 할 때 발생하는 예외
 */
public class DuplicateCouponIssueException extends BusinessException {
    public DuplicateCouponIssueException(String couponId) {
        super(ErrorCode.C001, "이미 발급받은 쿠폰입니다: " + couponId);
    }
}
