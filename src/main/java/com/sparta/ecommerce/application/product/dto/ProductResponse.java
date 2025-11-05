package com.sparta.ecommerce.application.product.dto;


import com.sparta.ecommerce.domain.product.Product;

public record ProductResponse(
        String productId,
        String name,
        double price,
        int stock,
        String category
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getStock().quantity(),  // Stock VO에서 quantity 추출
                product.getCategory()
        );
    }
}
