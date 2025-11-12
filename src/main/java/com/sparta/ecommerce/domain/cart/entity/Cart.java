package com.sparta.ecommerce.domain.cart.entity;

import com.sparta.ecommerce.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 엔티티
 * Cart는 사용자의 장바구니를 나타내며, 실제 상품 목록은 CartItem에 저장됨
 */
@Entity
@Table(name = "carts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Cart extends BaseEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String cartId;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;
}
