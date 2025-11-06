package com.sparta.ecommerce.domain.cart;

import java.util.Optional;

/**
 * 장바구니 Repository 인터페이스
 */
public interface CartRepository {

    /**
     * 장바구니 저장
     */
    void save(Cart cart);

    /**
     * 사용자 ID로 장바구니 조회
     */
    Optional<Cart> findByUserId(String userId);

    /**
     * 장바구니 ID로 조회
     */
    Optional<Cart> findById(String cartId);

    /**
     * 장바구니 삭제
     */
    void delete(String cartId);
}
