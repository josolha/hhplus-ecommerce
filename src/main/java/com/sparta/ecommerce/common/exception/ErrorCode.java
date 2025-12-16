package com.sparta.ecommerce.common.exception;

/**
 * 에러 코드 정의
 * API 설계 문서(API_DESIGN.md)에 정의된 에러 코드
 */
public enum ErrorCode {
    // 상품 관련 에러
    P001("P001", "상품을 찾을 수 없습니다"),
    P002("P002", "재고가 부족합니다"),

    // 주문 관련 에러
    O001("O001", "유효하지 않은 주문 수량입니다"),
    O002("O002", "주문을 찾을 수 없습니다"),

    // 결제 관련 에러
    PAY001("PAY001", "잔액이 부족합니다"),
    PAY002("PAY002", "결제 처리에 실패했습니다"),

    // 쿠폰 관련 에러
    C001("C001", "쿠폰이 모두 소진되었습니다"),
    C002("C002", "유효하지 않은 쿠폰입니다"),
    C003("C003", "만료된 쿠폰입니다"),
    C004("C004", "이미 사용된 쿠폰입니다"),
    C005("C005", "쿠폰 발급 처리 중입니다. 잠시 후 다시 시도해주세요"),

    // 공통 에러
    COMMON001("COMMON001", "필수 파라미터가 누락되었습니다"),
    COMMON002("COMMON002", "잘못된 요청 형식입니다"),
    COMMON003("COMMON003", "인증이 필요합니다"),
    COMMON004("COMMON004", "서버 내부 오류가 발생했습니다");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
