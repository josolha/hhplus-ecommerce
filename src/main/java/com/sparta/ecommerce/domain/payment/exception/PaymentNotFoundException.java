package com.sparta.ecommerce.domain.payment.exception;

/**
 * 결제를 찾을 수 없을 때 발생하는 예외
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String paymentId) {
        super("결제를 찾을 수 없습니다: " + paymentId);
    }
}
