package com.sparta.ecommerce.application.order.dto;

import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        @Schema(description = "주문 ID", example = "ORD20251106001")
        String orderId,
        @Schema(description = "주문 항목 목록")
        List<OrderItemResponse> items,
        @Schema(description = "총 금액", example = "1500000")
        long totalAmount,
        @Schema(description = "할인 금액", example = "50000")
        long discountAmount,
        @Schema(description = "최종 결제 금액", example = "1450000")
        long finalAmount,
        @Schema(description = "결제 후 잔액", example = "550000")
        long remainingBalance,
        @Schema(description = "주문 생성 일시", example = "2025-11-06T22:00:00")
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order, List<OrderItem> orderItems, long remainingBalance) {
        List<OrderItemResponse> itemResponses = orderItems.stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.getOrderId(),
                itemResponses,
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                remainingBalance,
                order.getCreatedAt()
        );
    }
}
