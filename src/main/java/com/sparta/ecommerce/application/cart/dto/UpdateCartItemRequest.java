package com.sparta.ecommerce.application.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * 장바구니 항목 수량 변경 요청 DTO
 */
public record UpdateCartItemRequest(
        @Schema(description = "변경할 수량", example = "3")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        int quantity
) {
}
