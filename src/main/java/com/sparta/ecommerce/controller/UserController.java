package com.sparta.ecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 사용자 API
 */
@Tag(name = "사용자 관리", description = "사용자 잔액 및 쿠폰 관리 API")
@RestController
@RequestMapping("/api/users")
public class UserController {

    /**
     * 잔액 조회
     * GET /api/users/balance
     */
    @Operation(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다")
    @GetMapping("/balance")
    public ResponseEntity<?> getBalance(
            @Parameter(description = "사용자 ID") @RequestParam String userId) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "userId", userId,
            "balance", 2000000
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 잔액 충전
     * POST /api/users/balance/charge
     */
    @Operation(summary = "잔액 충전", description = "사용자의 잔액을 충전합니다")
    @PostMapping("/balance/charge")
    public ResponseEntity<?> chargeBalance(@RequestBody Object chargeRequest) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "userId", "user123",
            "previousBalance", 2000000,
            "chargedAmount", 500000,
            "currentBalance", 2500000,
            "chargedAt", "2025-10-30T14:40:00"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 내 쿠폰 목록 조회
     * GET /api/users/coupons
     */
    @Operation(summary = "내 쿠폰 목록 조회", description = "사용자가 보유한 쿠폰 목록을 조회합니다")
    @GetMapping("/coupons")
    public ResponseEntity<?> getUserCoupons(
            @Parameter(description = "사용자 ID") @RequestParam String userId,
            @Parameter(description = "쿠폰 상태 (available/used/expired)") @RequestParam(required = false) String status) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "coupons", List.of(
                Map.of(
                    "userCouponId", "UC001",
                    "couponId", "C001",
                    "name", "신규 가입 5만원 할인 쿠폰",
                    "discountType", "fixed",
                    "discountValue", 50000,
                    "status", "available",
                    "issuedAt", "2025-10-25T10:00:00",
                    "expiresAt", "2025-12-31T23:59:59"
                ),
                Map.of(
                    "userCouponId", "UC002",
                    "couponId", "C002",
                    "name", "10% 할인 쿠폰",
                    "discountType", "percentage",
                    "discountValue", 10,
                    "status", "used",
                    "issuedAt", "2025-10-20T15:30:00",
                    "usedAt", "2025-10-28T11:20:00",
                    "expiresAt", "2025-11-30T23:59:59"
                )
            )
        );

        return ResponseEntity.ok(response);
    }
}
