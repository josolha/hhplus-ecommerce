package com.sparta.ecommerce.application.cart;

import com.sparta.ecommerce.application.cart.dto.CartItemResponse;
import com.sparta.ecommerce.application.cart.dto.UpdateCartItemRequest;
import com.sparta.ecommerce.domain.cart.Cart;
import com.sparta.ecommerce.domain.cart.CartItem;
import com.sparta.ecommerce.domain.cart.CartRepository;
import com.sparta.ecommerce.domain.cart.exception.CartItemNotFoundException;
import com.sparta.ecommerce.domain.cart.exception.CartNotFoundException;
import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장바구니 항목 수량 변경 UseCase
 */
@Service
@RequiredArgsConstructor
public class UpdateCartItemUseCase {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    /**
     * 장바구니 항목 수량 변경
     * @param userId 사용자 ID
     * @param cartItemId 장바구니 항목 ID
     * @param request 수량 변경 요청
     * @return 변경된 장바구니 항목 정보
     */
    public CartItemResponse execute(String userId, String cartItemId, UpdateCartItemRequest request) {
        // 1. 사용자의 장바구니 조회
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        // 2. 해당 항목 찾기
        CartItem targetItem = cart.getItems().stream()
                .filter(item -> item.getCartItemId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));

        // 3. 수량 변경
        Cart updatedCart = cart.updateItemQuantity(cartItemId, request.quantity());
        cartRepository.save(updatedCart);

        // 4. 상품 정보 조회 및 응답 생성
        Product product = productRepository.findById(targetItem.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(targetItem.getProductId()));

        CartItem updatedItem = updatedCart.getItems().stream()
                .filter(item -> item.getCartItemId().equals(cartItemId))
                .findFirst()
                .orElseThrow();

        return CartItemResponse.from(updatedItem, product);
    }
}
