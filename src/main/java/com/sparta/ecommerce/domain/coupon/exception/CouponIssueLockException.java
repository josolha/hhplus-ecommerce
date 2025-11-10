package com.sparta.ecommerce.domain.coupon.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 쿠폰 발급 Lock 획득 실패 시 발생하는 예외
 * 동시성 제어로 인해 쿠폰 발급 처리가 진행 중일 때 발생
 */
public class CouponIssueLockException extends BusinessException {
    public CouponIssueLockException() {
        super(ErrorCode.C005);
    }

    public CouponIssueLockException(String message) {
        super(ErrorCode.C005, message);
    }

    public CouponIssueLockException(String couponId, long waitTimeSeconds) {
        super(ErrorCode.C005,
            String.format("쿠폰[%s] 발급 처리 중입니다. %d초 동안 대기했으나 Lock을 획득하지 못했습니다.",
                couponId, waitTimeSeconds));
    }
}
