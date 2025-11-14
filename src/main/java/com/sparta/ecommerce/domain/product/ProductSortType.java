package com.sparta.ecommerce.domain.product;

import java.util.Arrays;

/**
 * 상품 정렬 타입
 */
public enum ProductSortType {

    PRICE("price", "가격순"),
    POPULARITY("popularity", "인기순"),
    NEWEST("newest", "최신순");

    private final String code;
    private final String description;

    ProductSortType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * code로부터 SortType 찾기
     * @param code 정렬 코드 ("price", "popularity", "newest")
     * @return SortType 또는 null
     */
    public static ProductSortType from(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
