package com.sparta.ecommerce.application.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 장바구니 상품 추가 요청 DTO
 */
public record AddToCartRequest(
        @Schema(description = "사용자 ID", example = "U001")
        @NotBlank(message = "사용자 ID는 필수입니다")
        String userId,

        @Schema(description = "상품 ID", example = "P001")
        @NotBlank(message = "상품 ID는 필수입니다")
        String productId,

        @Schema(description = "수량", example = "2")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        int quantity
) {
}
