package com.sparta.ecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 상품 관리 API
 */
@Tag(name = "상품 관리", description = "상품 조회 및 재고 관리 API")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /**
     * 상품 목록 조회
     * GET /api/products
     */
    @Operation(summary = "상품 목록 조회", description = "카테고리별 필터링 및 정렬 기능을 제공합니다")
    @GetMapping
    public ResponseEntity<?> getProducts(
            @Parameter(description = "카테고리별 필터링") @RequestParam(required = false) String category,
            @Parameter(description = "정렬 기준 (price/popularity/newest)") @RequestParam(required = false) String sort) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "products", List.of(
                Map.of(
                    "productId", "P001",
                    "name", "노트북",
                    "price", 1500000,
                    "stock", 10,
                    "category", "전자제품"
                ),
                Map.of(
                    "productId", "P002",
                    "name", "무선 마우스",
                    "price", 35000,
                    "stock", 50,
                    "category", "전자제품"
                ),
                Map.of(
                    "productId", "P003",
                    "name", "키보드",
                    "price", 89000,
                    "stock", 25,
                    "category", "전자제품"
                )
            )
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 상품 상세 조회
     * GET /api/products/{productId}
     */
    @Operation(summary = "상품 상세 조회", description = "특정 상품의 상세 정보를 조회합니다")
    @GetMapping("/{productId}")
    public ResponseEntity<?> getProductDetail(
            @Parameter(description = "상품 ID") @PathVariable String productId) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "productId", productId,
            "name", "노트북",
            "price", 1500000,
            "stock", 10,
            "category", "전자제품",
            "description", "고성능 노트북입니다. Intel i7 프로세서, 16GB RAM, 512GB SSD"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 재고 실시간 확인
     * GET /api/products/{productId}/stock
     */
    @Operation(summary = "재고 실시간 확인", description = "상품의 현재 재고를 실시간으로 확인합니다")
    @GetMapping("/{productId}/stock")
    public ResponseEntity<?> getProductStock(
            @Parameter(description = "상품 ID") @PathVariable String productId) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "productId", productId,
            "stock", 10,
            "available", true
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 인기 상품 조회 (최근 3일, Top 5)
     * GET /api/products/popular
     */
    @Operation(summary = "인기 상품 조회", description = "최근 N일간의 인기 상품 Top N을 조회합니다")
    @GetMapping("/popular")
    public ResponseEntity<?> getPopularProducts(
            @Parameter(description = "조회 기간 (일 단위)") @RequestParam(defaultValue = "3") int days,
            @Parameter(description = "조회할 상품 개수") @RequestParam(defaultValue = "5") int limit) {

        // Mock 데이터
        Map<String, Object> response = Map.of(
            "period", Map.of(
                "days", days,
                "startDate", "2025-10-27",
                "endDate", "2025-10-30"
            ),
            "products", List.of(
                Map.of(
                    "productId", "P001",
                    "name", "노트북",
                    "price", 1500000,
                    "stock", 10,
                    "orderCount", 150,
                    "rank", 1
                ),
                Map.of(
                    "productId", "P002",
                    "name", "무선 마우스",
                    "price", 35000,
                    "stock", 50,
                    "orderCount", 120,
                    "rank", 2
                ),
                Map.of(
                    "productId", "P003",
                    "name", "키보드",
                    "price", 89000,
                    "stock", 25,
                    "orderCount", 95,
                    "rank", 3
                )
            )
        );

        return ResponseEntity.ok(response);
    }
}
