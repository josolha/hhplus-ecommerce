package com.sparta.ecommerce.application.product.dto;

import com.sparta.ecommerce.domain.product.Product;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProductStockResponse(
        @Schema(description = "상품 ID", example = "P001")
        String productId,

        @Schema(description = "재고 수량", example = "10")
        int stock,

        @Schema(description = "재고 있음 여부", example = "true")
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
