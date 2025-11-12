package com.sparta.ecommerce.application.cart;

import com.sparta.ecommerce.application.cart.dto.AddToCartRequest;
import com.sparta.ecommerce.application.cart.dto.CartItemResponse;
import com.sparta.ecommerce.domain.cart.entity.Cart;
import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.cart.repository.CartRepository;
import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.InsufficientStockException;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 장바구니 상품 추가 UseCase
 */
@Service
@RequiredArgsConstructor
public class AddToCartUseCase {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
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

        // 2. 상품 수량 확인
        if(!product.canAddToCart(request.quantity())){
            throw new InsufficientStockException();
        }

        // 3. 사용자의 장바구니 조회 또는 생성
        Cart cart = cartRepository.findByUserId(request.userId())
                .orElseGet(() -> {
                    Cart newCart = createNewCart(request.userId());
                    cartRepository.save(newCart);
                    return newCart;
                });

        // 4. 기존 장바구니 아이템 확인 (같은 상품이 이미 있는지)
        Optional<CartItem> existingItem = cartItemRepository
                .findByCartIdAndProductId(cart.getCartId(), request.productId());

        CartItem savedItem;
        if (existingItem.isPresent()) {
            // 5-1. 기존 아이템이 있으면 수량 증가
            CartItem existing = existingItem.get();
            CartItem updated = existing.addQuantity(request.quantity());
            savedItem = cartItemRepository.save(updated);
        } else {
            // 5-2. 새 아이템 추가
            CartItem newItem = CartItem.builder()
                    .cartId(cart.getCartId())
                    .productId(request.productId())
                    .quantity(request.quantity())
                    .build();
            savedItem = cartItemRepository.save(newItem);
        }

        // 6. 응답 생성
        return CartItemResponse.from(savedItem, product);
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
