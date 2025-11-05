package com.sparta.ecommerce.domain.user.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 잔액이 부족할 때 발생하는 예외
 */
public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException() {
        super(ErrorCode.PAY001);
    }

    public InsufficientBalanceException(String message) {
        super(ErrorCode.PAY001, message);
    }

    public InsufficientBalanceException(long required, long current) {
        super(ErrorCode.PAY001,
              String.format("잔액이 부족합니다. 필요 금액: %d, 현재 잔액: %d", required, current));
    }
}
