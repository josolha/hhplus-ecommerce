package com.sparta.ecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 외부 연동 API (내부 시스템용)
 */
@Tag(name = "관리자", description = "외부 데이터 연동 관리 API")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * 주문 데이터 전송 상태 조회
     * GET /api/admin/orders/{orderId}/sync-status
     */
    @Operation(summary = "주문 데이터 전송 상태 조회", description = "외부 시스템으로의 주문 데이터 전송 상태를 조회합니다")
    @GetMapping("/orders/{orderId}/sync-status")
    public ResponseEntity<?> getOrderSyncStatus(
            @Parameter(description = "주문 ID") @PathVariable String orderId) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "orderId", orderId,
            "synced", true,
            "syncedAt", "2025-10-30T14:31:00",
            "syncAttempts", 1,
            "lastSyncError", null
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 주문 데이터 재전송
     * POST /api/admin/orders/{orderId}/resync
     */
    @Operation(summary = "주문 데이터 재전송", description = "실패한 주문 데이터를 외부 시스템으로 재전송합니다")
    @PostMapping("/orders/{orderId}/resync")
    public ResponseEntity<?> resyncOrder(
            @Parameter(description = "주문 ID") @PathVariable String orderId) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "orderId", orderId,
            "synced", true,
            "syncedAt", "2025-10-30T14:45:00",
            "message", "주문 데이터가 재전송되었습니다"
        );

        return ResponseEntity.ok(response);
    }
}
