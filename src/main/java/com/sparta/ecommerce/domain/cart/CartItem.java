package com.sparta.ecommerce.domain.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 항목 엔티티
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    private String cartItemId;
    private String productId;
    private int quantity;

    /**
     * 수량 변경
     */
    public CartItem updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다");
        }
        return CartItem.builder()
                .cartItemId(this.cartItemId)
                .productId(this.productId)
                .quantity(newQuantity)
                .build();
    }

    /**
     * 수량 증가
     */
    public CartItem addQuantity(int additionalQuantity) {
        if (additionalQuantity <= 0) {
            throw new IllegalArgumentException("추가 수량은 1개 이상이어야 합니다");
        }
        return CartItem.builder()
                .cartItemId(this.cartItemId)
                .productId(this.productId)
                .quantity(this.quantity + additionalQuantity)
                .build();
    }
}
