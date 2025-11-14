package com.sparta.ecommerce.application.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 주문 목록 조회 응답 (페이징 포함)
 */
public record OrderListResponse(
        @Schema(description = "주문 목록")
        List<OrderSummaryResponse> orders,
        @Schema(description = "현재 페이지", example = "1")
        int currentPage,
        @Schema(description = "전체 페이지 수", example = "3")
        int totalPages,
        @Schema(description = "전체 주문 개수", example = "25")
        int totalCount
) {
}
