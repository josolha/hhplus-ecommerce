package com.sparta.ecommerce.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 장바구니 관리 API
 */
@Tag(name = "장바구니 관리", description = "장바구니 상품 추가/수정/삭제 API")
@RestController
@RequestMapping("/api/cart")
public class CartController {

    /**
     * 장바구니 조회
     * GET /api/cart
     */
    @Operation(summary = "장바구니 조회", description = "사용자의 장바구니 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<?> getCart(
            @Parameter(description = "사용자 ID") @RequestParam String userId) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "items", List.of(
                Map.of(
                    "cartItemId", "CART001",
                    "productId", "P001",
                    "productName", "노트북",
                    "price", 1500000,
                    "quantity", 1,
                    "subtotal", 1500000
                ),
                Map.of(
                    "cartItemId", "CART002",
                    "productId", "P002",
                    "productName", "무선 마우스",
                    "price", 35000,
                    "quantity", 2,
                    "subtotal", 70000
                )
            ),
            "totalAmount", 1570000
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 상품 추가
     * POST /api/cart/items
     */
    @Operation(summary = "장바구니 상품 추가", description = "장바구니에 상품을 추가합니다")
    @PostMapping("/items")
    public ResponseEntity<?> addCartItem(@RequestBody Object cartItemRequest) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "cartItemId", "CART003",
            "productId", "P003",
            "quantity", 1,
            "message", "장바구니에 추가되었습니다"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 상품 수량 변경
     * PATCH /api/cart/items/{cartItemId}
     */
    @Operation(summary = "장바구니 상품 수량 변경", description = "장바구니 상품의 수량을 변경합니다")
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<?> updateCartItemQuantity(
            @Parameter(description = "장바구니 항목 ID") @PathVariable String cartItemId,
            @RequestBody Object quantityRequest) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "cartItemId", cartItemId,
            "quantity", 3,
            "message", "수량이 변경되었습니다"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 상품 삭제
     * DELETE /api/cart/items/{cartItemId}
     */
    @Operation(summary = "장바구니 상품 삭제", description = "장바구니에서 상품을 삭제합니다")
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<?> deleteCartItem(
            @Parameter(description = "장바구니 항목 ID") @PathVariable String cartItemId) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "message", "장바구니에서 삭제되었습니다"
        );

        return ResponseEntity.ok(response);
    }
}
