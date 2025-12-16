package com.sparta.ecommerce.application.cart.usecase;

import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.cart.exception.CartItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 항목 삭제 UseCase
 */
@Service
@RequiredArgsConstructor
public class RemoveFromCartUseCase {

    private final CartItemRepository cartItemRepository;

    /**
     * 장바구니 항목 삭제
     * @param userId 사용자 ID
     * @param cartItemId 장바구니 항목 ID
     */
    @Transactional
    public void execute(String userId, String cartItemId) {
        // 1. 장바구니 아이템 존재 확인
        if (!cartItemRepository.findById(cartItemId).isPresent()) {
            throw new CartItemNotFoundException(cartItemId);
        }

        // 2. 항목 삭제
        cartItemRepository.deleteById(cartItemId);
    }
}
