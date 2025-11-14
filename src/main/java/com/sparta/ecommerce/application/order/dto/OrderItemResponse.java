package com.sparta.ecommerce.application.order.dto;

import com.sparta.ecommerce.domain.order.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record OrderItemResponse(
        @Schema(description = "상품 ID", example = "P001")
        String productId,
        @Schema(description = "상품명", example = "노트북")
        String productName,
        @Schema(description = "단가", example = "1500000")
        long price,
        @Schema(description = "수량", example = "2")
        int quantity,
        @Schema(description = "소계", example = "3000000")
        long subtotal
) {
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getUnitPrice(),
                orderItem.getQuantity(),
                orderItem.getSubtotal()
        );
    }
}
