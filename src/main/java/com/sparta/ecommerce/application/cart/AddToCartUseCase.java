package com.sparta.ecommerce.application.cart;

import com.sparta.ecommerce.application.cart.dto.AddToCartRequest;
import com.sparta.ecommerce.application.cart.dto.CartItemResponse;
import com.sparta.ecommerce.domain.cart.Cart;
import com.sparta.ecommerce.domain.cart.CartItem;
import com.sparta.ecommerce.domain.cart.CartRepository;
import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 장바구니 상품 추가 UseCase
 */
@Service
@RequiredArgsConstructor
public class AddToCartUseCase {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    /**
     * 장바구니에 상품 추가
     * @param request 추가 요청 정보
     * @return 추가된 장바구니 항목 정보
     */
    public CartItemResponse execute(AddToCartRequest request) {
        // 1. 상품 존재 확인
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        // 2. 사용자의 장바구니 조회 또는 생성
        Cart cart = cartRepository.findByUserId(request.userId())
                .orElseGet(() -> createNewCart(request.userId()));

        // 3. 장바구니 항목 생성
        String cartItemId = "CART" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CartItem newItem = CartItem.builder()
                .cartItemId(cartItemId)
                .productId(request.productId())
                .quantity(request.quantity())
                .build();

        // 4. 장바구니에 상품 추가 (기존 상품이면 수량 증가)
        Cart updatedCart = cart.addItem(newItem);
        cartRepository.save(updatedCart);

        // 5. 응답 생성
        CartItem addedItem = updatedCart.getItems().stream()
                .filter(item -> item.getProductId().equals(request.productId()))
                .findFirst()
                .orElseThrow();

        return CartItemResponse.from(addedItem, product);
    }

    /**
     * 새 장바구니 생성
     */
    private Cart createNewCart(String userId) {
        String cartId = "CART_" + userId;
        return Cart.builder()
                .cartId(cartId)
                .userId(userId)
                .build();
    }
}
