package com.sparta.ecommerce.domain.payment.exception;

/**
 * 결제 실패 예외
 */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) {
        super(message);
    }

    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
