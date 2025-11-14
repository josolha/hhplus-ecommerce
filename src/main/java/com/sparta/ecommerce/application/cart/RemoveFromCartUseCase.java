package com.sparta.ecommerce.application.cart;

import com.sparta.ecommerce.domain.cart.Cart;
import com.sparta.ecommerce.domain.cart.CartRepository;
import com.sparta.ecommerce.domain.cart.exception.CartItemNotFoundException;
import com.sparta.ecommerce.domain.cart.exception.CartNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장바구니 항목 삭제 UseCase
 */
@Service
@RequiredArgsConstructor
public class RemoveFromCartUseCase {

    private final CartRepository cartRepository;

    /**
     * 장바구니 항목 삭제
     * @param userId 사용자 ID
     * @param cartItemId 장바구니 항목 ID
     */
    public void execute(String userId, String cartItemId) {
        // 1. 사용자의 장바구니 조회
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException(userId));

        // 2. 해당 항목이 존재하는지 확인
        boolean exists = cart.getItems().stream()
                .anyMatch(item -> item.getCartItemId().equals(cartItemId));

        if (!exists) {
            throw new CartItemNotFoundException(cartItemId);
        }

        // 3. 항목 삭제
        Cart updatedCart = cart.removeItem(cartItemId);
        cartRepository.save(updatedCart);
    }
}
