package com.sparta.ecommerce.domain.product.entity;

import com.sparta.ecommerce.domain.common.BaseEntity;
import com.sparta.ecommerce.domain.product.vo.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 엔티티
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_category_price", columnList = "category, price")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Product extends BaseEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false)
    private long price;

    @Embedded
    private Stock stock;

    @Column(name = "category")
    private String category;

    /*장바구니 담기 전용*/
    public boolean canAddToCart(int requestQuantity) {
        return stock.isAvailable(requestQuantity);
    }

    /*결제시 검증 + 차감용*/
    public void reserveStock(int requestQuantity) {
        this.stock = stock.decrease(requestQuantity);
    }
}

