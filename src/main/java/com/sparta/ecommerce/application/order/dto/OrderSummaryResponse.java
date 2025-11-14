package com.sparta.ecommerce.application.order.dto;

import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 주문 목록 조회용 요약 정보
 */
public record OrderSummaryResponse(
        @Schema(description = "주문 ID", example = "ORD20251106001")
        String orderId,
        @Schema(description = "총 금액", example = "1500000")
        long totalAmount,
        @Schema(description = "할인 금액", example = "50000")
        long discountAmount,
        @Schema(description = "최종 결제 금액", example = "1450000")
        long finalAmount,
        @Schema(description = "주문 상태", example = "COMPLETED")
        OrderStatus status,
        @Schema(description = "주문 생성 일시", example = "2025-11-06T22:00:00")
        LocalDateTime createdAt
) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getOrderId(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
