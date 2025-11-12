package com.sparta.ecommerce.application.cart;

import com.sparta.ecommerce.application.cart.dto.CartItemResponse;
import com.sparta.ecommerce.application.cart.dto.UpdateCartItemRequest;
import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.cart.exception.CartItemNotFoundException;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장바구니 항목 수량 변경 UseCase
 */
@Service
@RequiredArgsConstructor
public class UpdateCartItemUseCase {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    /**
     * 장바구니 항목 수량 변경
     * @param userId 사용자 ID
     * @param cartItemId 장바구니 항목 ID
     * @param request 수량 변경 요청
     * @return 변경된 장바구니 항목 정보
     */
    public CartItemResponse execute(String userId, String cartItemId, UpdateCartItemRequest request) {
        // 1. 장바구니 아이템 조회
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));

        // 2. 수량 변경
        CartItem updatedItem = cartItem.updateQuantity(request.quantity());
        cartItemRepository.save(updatedItem);

        // 3. 상품 정보 조회 및 응답 생성
        Product product = productRepository.findById(updatedItem.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(updatedItem.getProductId()));

        return CartItemResponse.from(updatedItem, product);
    }
}
