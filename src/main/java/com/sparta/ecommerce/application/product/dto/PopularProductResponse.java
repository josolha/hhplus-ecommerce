package com.sparta.ecommerce.application.product.dto;

import com.sparta.ecommerce.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인기 상품 응답 DTO
 * 판매량 정보를 포함한 상품 정보
 */
public record PopularProductResponse(
        @Schema(description = "상품 ID", example = "P001")
        String productId,

        @Schema(description = "상품명", example = "노트북")
        String name,

        @Schema(description = "가격", example = "1500000")
        Long price,

        @Schema(description = "재고 수량", example = "10")
        Integer stock,

        @Schema(description = "카테고리", example = "전자제품")
        String category,

        @Schema(description = "판매량 (최근 N일간)", example = "1250")
        Long salesCount
) {
    /**
     * JPQL DTO Projection용 생성자
     * OrderItemRepository의 쿼리에서 직접 호출됨
     */
    public PopularProductResponse(
            String productId,
            String name,
            Long price,
            Integer stock,
            String category,
            Long salesCount
    ) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.category = category;
        this.salesCount = salesCount;
    }

    /**
     * Product 엔티티로부터 변환 (Redis 랭킹용)
     * 판매량 정보는 없음 (Redis에는 순위만 있고, 판매량은 DB 집계 시만 있음)
     */
    public static PopularProductResponse from(Product product) {
        return new PopularProductResponse(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getStock().getQuantity(),
                product.getCategory(),
                null  // Redis 랭킹 방식에서는 판매량 정보 없음
        );
    }
}
