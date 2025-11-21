package com.sparta.ecommerce.domain.payment;

/**
 * 결제 수단
 */
public enum PaymentMethod {
    BALANCE("잔액"),
    CARD("카드"),
    KAKAO_PAY("카카오페이"),
    TOSS_PAY("토스페이");

    private final String description;

    PaymentMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
