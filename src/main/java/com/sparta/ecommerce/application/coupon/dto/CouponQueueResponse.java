package com.sparta.ecommerce.application.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 쿠폰 발급 큐 응답 DTO
 */
public record CouponQueueResponse(
        @Schema(description = "큐 추가 성공 여부", example = "true")
        boolean queued,

        @Schema(description = "응답 메시지", example = "쿠폰 발급 요청이 접수되었습니다.")
        String message,

        @Schema(description = "대기 순번", example = "15")
        long queueSize
) {
}
