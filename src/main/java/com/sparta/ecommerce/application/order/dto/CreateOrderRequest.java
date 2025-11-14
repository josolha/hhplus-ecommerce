package com.sparta.ecommerce.application.order.dto;

import com.sparta.ecommerce.domain.order.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateOrderRequest (
        @Schema(description = "사용자 ID", example = "U001")
        @NotBlank(message = "사용자 ID는 필수입니다")
        String userId,

        @Schema(description = "쿠폰 ID(선택)", example = "C001")
        String couponId
){

}
