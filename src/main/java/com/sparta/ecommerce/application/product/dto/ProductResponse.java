package com.sparta.ecommerce.application.product.dto;


import com.sparta.ecommerce.domain.product.Product;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProductResponse(
        @Schema(description = "상품 ID", example = "P001")
        String productId,

        @Schema(description = "상품명", example = "노트북")
        String name,

        @Schema(description = "가격", example = "1500000")
        double price,

        @Schema(description = "재고 수량", example = "10")
        int stock,

        @Schema(description = "카테고리", example = "전자제품")
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
