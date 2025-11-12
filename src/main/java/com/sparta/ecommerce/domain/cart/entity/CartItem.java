package com.sparta.ecommerce.domain.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 장바구니 항목 엔티티
 */
@Entity
@Table(name = "cart_items")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CartItem {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String cartItemId;

    @Column(name = "cart_id", nullable = false)
    private String cartId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        if (this.addedAt == null) {
            this.addedAt = LocalDateTime.now();
        }
    }

    /**
     * 수량 변경
     */
    public CartItem updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다");
        }
        return CartItem.builder()
                .cartItemId(this.cartItemId)
                .cartId(this.cartId)
                .productId(this.productId)
                .quantity(newQuantity)
                .addedAt(this.addedAt)
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
                .cartId(this.cartId)
                .productId(this.productId)
                .quantity(this.quantity + additionalQuantity)
                .addedAt(this.addedAt)
                .build();
    }
}
