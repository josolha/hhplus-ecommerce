package com.sparta.ecommerce.application.cart;

import com.sparta.ecommerce.application.cart.dto.CartItemResponse;
import com.sparta.ecommerce.application.cart.dto.CartResponse;
import com.sparta.ecommerce.domain.cart.Cart;
import com.sparta.ecommerce.domain.cart.CartRepository;
import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 장바구니 조회 UseCase
 */
@Service
@RequiredArgsConstructor
public class GetCartUseCase {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    /**
     * 사용자의 장바구니 조회
     * @param userId 사용자 ID
     * @return 장바구니 정보
     */
    public CartResponse execute(String userId) {
        // 1. 사용자의 장바구니 조회
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> createEmptyCart(userId));

        // 2. 각 항목에 대한 상품 정보 조회하여 응답 생성
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(cartItem -> {
                    Product product = productRepository.findById(cartItem.getProductId())
                            .orElseThrow(() -> new ProductNotFoundException(cartItem.getProductId()));
                    return CartItemResponse.from(cartItem, product);
                })
                .toList();

        // 3. 응답 생성
        return CartResponse.of(cart.getCartId(), cart.getUserId(), itemResponses);
    }

    /**
     * 빈 장바구니 생성
     */
    private Cart createEmptyCart(String userId) {
        return Cart.builder()
                .cartId("CART_" + userId)
                .userId(userId)
                .items(new ArrayList<>())
                .build();
    }
}
