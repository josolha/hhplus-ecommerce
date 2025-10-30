package com.sparta.ecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 쿠폰 시스템 API
 */
@Tag(name = "쿠폰 시스템", description = "쿠폰 발급 및 검증 API")
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    /**
     * 쿠폰 목록 조회 (발급 가능한 쿠폰)
     * GET /api/coupons
     */
    @Operation(summary = "쿠폰 목록 조회", description = "발급 가능한 쿠폰 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<?> getCoupons() {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "coupons", List.of(
                Map.of(
                    "couponId", "C001",
                    "name", "신규 가입 5만원 할인 쿠폰",
                    "discountType", "fixed",
                    "discountValue", 50000,
                    "maxQuantity", 100,
                    "issuedQuantity", 45,
                    "remainingQuantity", 55,
                    "expiresAt", "2025-12-31T23:59:59"
                ),
                Map.of(
                    "couponId", "C002",
                    "name", "10% 할인 쿠폰",
                    "discountType", "percentage",
                    "discountValue", 10,
                    "maxQuantity", 50,
                    "issuedQuantity", 48,
                    "remainingQuantity", 2,
                    "expiresAt", "2025-11-30T23:59:59"
                )
            )
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰 발급 (선착순)
     * POST /api/coupons/{couponId}/issue
     */
    @Operation(summary = "쿠폰 발급", description = "선착순으로 쿠폰을 발급합니다")
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<?> issueCoupon(
            @Parameter(description = "쿠폰 ID") @PathVariable String couponId,
            @RequestBody Object issueRequest) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "userCouponId", "UC001",
            "couponId", couponId,
            "name", "신규 가입 5만원 할인 쿠폰",
            "discountType", "fixed",
            "discountValue", 50000,
            "issuedAt", "2025-10-30T14:35:00",
            "expiresAt", "2025-12-31T23:59:59"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰 유효성 검증
     * POST /api/coupons/validate
     */
    @Operation(summary = "쿠폰 유효성 검증", description = "쿠폰 사용 가능 여부를 검증하고 할인 금액을 계산합니다")
    @PostMapping("/validate")
    public ResponseEntity<?> validateCoupon(@RequestBody Object validateRequest) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "valid", true,
            "discountAmount", 50000,
            "finalAmount", 1450000,
            "message", "쿠폰이 정상적으로 적용되었습니다"
        );

        return ResponseEntity.ok(response);
    }
}
