package com.sparta.ecommerce.application.order.dto;

import com.sparta.ecommerce.domain.order.Order;
import com.sparta.ecommerce.domain.order.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세 조회 응답
 */
public record OrderDetailResponse(
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
        @Schema(description = "쿠폰 ID", example = "C001")
        String couponId,
        @Schema(description = "주문 상태", example = "COMPLETED")
        OrderStatus status,
        @Schema(description = "주문 생성 일시", example = "2025-11-06T22:00:00")
        LocalDateTime createdAt
) {
    public static OrderDetailResponse from(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderDetailResponse(
                order.getOrderId(),
                itemResponses,
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getCouponId(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
