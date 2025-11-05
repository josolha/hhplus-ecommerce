package com.sparta.ecommerce.application.product.dto;

import com.sparta.ecommerce.domain.product.Product;

public record ProductStockResponse(
        String productId,
        int stock,
        boolean available
) {
    public static ProductStockResponse from(Product product) {
        return new ProductStockResponse(
                product.getProductId(),
                product.getStock().quantity(),
                !product.getStock().isOutOfStock()
        );
    }
}
