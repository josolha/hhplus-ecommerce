package com.sparta.ecommerce.application.cart;

import com.sparta.ecommerce.application.cart.dto.CartItemResponse;
import com.sparta.ecommerce.application.cart.dto.CartResponse;
import com.sparta.ecommerce.domain.cart.entity.Cart;
import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.cart.repository.CartRepository;
import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장바구니 조회 UseCase
 */
@Service
@RequiredArgsConstructor
public class GetCartUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    /**
     * 사용자의 장바구니 조회
     * @param userId 사용자 ID
     * @return 장바구니 정보
     */
    @Transactional(readOnly = true)
    public CartResponse execute(String userId) {
        // 1. 사용자의 장바구니 조회
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyCart(userId));

        // 2. 장바구니 아이템 조회
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getCartId());

        // 3. 각 항목에 대한 상품 정보 조회하여 응답 생성
        List<CartItemResponse> itemResponses = cartItems.stream()
                .map(cartItem -> {
                    Product product = productRepository.findById(cartItem.getProductId())
                            .orElseThrow(() -> new ProductNotFoundException(cartItem.getProductId()));
                    return CartItemResponse.from(cartItem, product);
                })
                .toList();

        // 4. 응답 생성
        return CartResponse.of(cart.getCartId(), cart.getUserId(), itemResponses);
    }

    /**
     * 빈 장바구니 생성
     */
    private Cart createEmptyCart(String userId) {
        return Cart.builder()
                .cartId("CART_" + userId)
                .userId(userId)
                .build();
    }
}
