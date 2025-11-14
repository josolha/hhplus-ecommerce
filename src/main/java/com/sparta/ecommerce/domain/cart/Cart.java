package com.sparta.ecommerce.domain.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 장바구니 엔티티
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {
    private String cartId;
    private String userId;
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    /**
     * 장바구니에 상품 추가
     * 이미 존재하는 상품이면 수량 증가
     */
    public Cart addItem(CartItem newItem) {
        List<CartItem> updatedItems = new ArrayList<>(this.items);

        // 이미 존재하는 상품인지 확인
        Optional<CartItem> existingItem = updatedItems.stream()
                .filter(item -> item.getProductId().equals(newItem.getProductId()))
                .findFirst();

        if (existingItem.isPresent()) {
            // 기존 항목의 수량 증가
            CartItem existing = existingItem.get();
            updatedItems.remove(existing);
            updatedItems.add(existing.addQuantity(newItem.getQuantity()));
        } else {
            // 새 항목 추가
            updatedItems.add(newItem);
        }

        return Cart.builder()
                .cartId(this.cartId)
                .userId(this.userId)
                .items(updatedItems)
                .build();
    }

    /**
     * 장바구니 항목 수량 변경
     */
    public Cart updateItemQuantity(String cartItemId, int newQuantity) {
        List<CartItem> updatedItems = new ArrayList<>();

        for (CartItem item : this.items) {
            if (item.getCartItemId().equals(cartItemId)) {
                updatedItems.add(item.updateQuantity(newQuantity));
            } else {
                updatedItems.add(item);
            }
        }

        return Cart.builder()
                .cartId(this.cartId)
                .userId(this.userId)
                .items(updatedItems)
                .build();
    }

    /**
     * 장바구니 항목 삭제
     */
    public Cart removeItem(String cartItemId) {
        List<CartItem> updatedItems = this.items.stream()
                .filter(item -> !item.getCartItemId().equals(cartItemId))
                .toList();

        return Cart.builder()
                .cartId(this.cartId)
                .userId(this.userId)
                .items(updatedItems)
                .build();
    }

    /**
     * 장바구니 비우기
     */
    public Cart clear() {
        return Cart.builder()
                .cartId(this.cartId)
                .userId(this.userId)
                .items(new ArrayList<>())
                .build();
    }

    /**
     * 장바구니가 비어있는지 확인
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 총 상품 개수
     */
    public int getTotalItemCount() {
        return items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }
}
