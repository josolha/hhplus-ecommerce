package com.sparta.ecommerce.presentation.controller.cart;

import com.sparta.ecommerce.application.cart.AddToCartUseCase;
import com.sparta.ecommerce.application.cart.GetCartUseCase;
import com.sparta.ecommerce.application.cart.RemoveFromCartUseCase;
import com.sparta.ecommerce.application.cart.UpdateCartItemUseCase;
import com.sparta.ecommerce.application.cart.dto.AddToCartRequest;
import com.sparta.ecommerce.application.cart.dto.CartItemResponse;
import com.sparta.ecommerce.application.cart.dto.CartResponse;
import com.sparta.ecommerce.application.cart.dto.UpdateCartItemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 장바구니 관리 API
 */
@Tag(name = "장바구니 관리", description = "장바구니 상품 추가/수정/삭제 API")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final AddToCartUseCase addToCartUseCase;
    private final GetCartUseCase getCartUseCase;
    private final UpdateCartItemUseCase updateCartItemUseCase;
    private final RemoveFromCartUseCase removeFromCartUseCase;

    /**
     * 장바구니 조회
     * GET /api/cart
     */
    @Operation(summary = "장바구니 조회", description = "사용자의 장바구니 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @Parameter(description = "사용자 ID") @RequestParam String userId) {

        CartResponse response = getCartUseCase.execute(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 상품 추가
     * POST /api/cart/items
     */
    @Operation(summary = "장바구니 상품 추가", description = "장바구니에 상품을 추가합니다")
    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addCartItem(
            @Valid @RequestBody AddToCartRequest request) {

        CartItemResponse response = addToCartUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 상품 수량 변경
     * PATCH /api/cart/items/{cartItemId}
     */
    @Operation(summary = "장바구니 상품 수량 변경", description = "장바구니 상품의 수량을 변경합니다")
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<CartItemResponse> updateCartItemQuantity(
            @Parameter(description = "사용자 ID") @RequestParam String userId,
            @Parameter(description = "장바구니 항목 ID") @PathVariable String cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        CartItemResponse response = updateCartItemUseCase.execute(userId, cartItemId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 장바구니 상품 삭제
     * DELETE /api/cart/items/{cartItemId}
     */
    @Operation(summary = "장바구니 상품 삭제", description = "장바구니에서 상품을 삭제합니다")
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Map<String, String>> deleteCartItem(
            @Parameter(description = "사용자 ID")
            @RequestParam String userId,
            @Parameter(description = "장바구니 항목 ID")
            @PathVariable String cartItemId) {

        removeFromCartUseCase.execute(userId, cartItemId);
        return ResponseEntity.ok(Map.of("message", "장바구니에서 삭제되었습니다"));
    }
}
