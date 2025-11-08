package com.sparta.ecommerce.domain.product;

import com.sparta.ecommerce.domain.product.vo.Stock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private String productId;
    private String name;
    private double price;
    private Stock stock;
    private String category;
    private String description;

    /*장바구니 담기 전용*/
    public boolean canAddToCart(int requestQuantity) {
        return stock.isAvailable(requestQuantity);
    }

    /*결제시 검증 + 차감용*/
    public void reserveStock(int requestQuantity) {
        this.stock = stock.decrease(requestQuantity);
    }
}

