package com.sparta.ecommerce.application.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProductRequest(

        @Schema(description = "상품 ID", example = "P001")
        String productId,

        @Schema(description = "상품명", example = "노트북")
        String name,

        @Schema(description = "가격", example = "1500000")
        double price,

        @Schema(description = "재고 수량", example = "10")
        int stock,

        @Schema(description = "카테고리", example = "전자제품")
        String category,

        @Schema(description = "설명", example = "고성능 노트북")
        String description
) {}
