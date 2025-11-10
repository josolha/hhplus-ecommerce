package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.cart.Cart;
import com.sparta.ecommerce.domain.cart.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 인메모리 장바구니 Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class InMemoryCartRepository implements CartRepository {

    private final InMemoryDataStore dataStore;

    @Override
    public void save(Cart cart) {
        dataStore.getCarts().put(cart.getCartId(), cart);
    }

    @Override
    public Optional<Cart> findByUserId(String userId) {
        return dataStore.getCarts().values().stream()
                .filter(cart -> cart.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public Optional<Cart> findById(String cartId) {
        return Optional.ofNullable(dataStore.getCarts().get(cartId));
    }

    @Override
    public void delete(String cartId) {
        dataStore.getCarts().remove(cartId);
    }
}
