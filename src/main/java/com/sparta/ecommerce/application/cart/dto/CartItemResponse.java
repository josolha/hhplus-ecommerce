package com.sparta.ecommerce.application.cart.dto;

import com.sparta.ecommerce.domain.cart.CartItem;
import com.sparta.ecommerce.domain.product.Product;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장바구니 항목 응답 DTO
 */
public record CartItemResponse(
        @Schema(description = "장바구니 항목 ID", example = "CART001")
        String cartItemId,

        @Schema(description = "상품 ID", example = "P001")
        String productId,

        @Schema(description = "상품명", example = "노트북")
        String productName,

        @Schema(description = "상품 가격", example = "1500000")
        long price,

        @Schema(description = "수량", example = "2")
        int quantity,

        @Schema(description = "소계", example = "3000000")
        long subtotal
) {
    /**
     * CartItem과 Product로 CartItemResponse 생성
     */
    public static CartItemResponse from(CartItem cartItem, Product product) {
        long price = (long) product.getPrice();
        long subtotal = price * cartItem.getQuantity();
        return new CartItemResponse(
                cartItem.getCartItemId(),
                cartItem.getProductId(),
                product.getName(),
                price,
                cartItem.getQuantity(),
                subtotal
        );
    }
}
