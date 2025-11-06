package com.sparta.ecommerce.application.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 장바구니 응답 DTO
 */
public record CartResponse(
        @Schema(description = "장바구니 ID", example = "CART_U001")
        String cartId,

        @Schema(description = "사용자 ID", example = "U001")
        String userId,

        @Schema(description = "장바구니 항목 목록")
        List<CartItemResponse> items,

        @Schema(description = "총 금액", example = "3500000")
        long totalAmount
) {
    /**
     * CartItemResponse 목록으로 CartResponse 생성
     */
    public static CartResponse of(String cartId, String userId, List<CartItemResponse> items) {
        long totalAmount = items.stream()
                .mapToLong(CartItemResponse::subtotal)
                .sum();

        return new CartResponse(cartId, userId, items, totalAmount);
    }
}
