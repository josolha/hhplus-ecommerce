package com.sparta.ecommerce.domain.cart.repository;

import com.sparta.ecommerce.domain.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 장바구니 Repository 인터페이스
 */
public interface CartRepository extends JpaRepository<Cart, String> {

    /**
     * 사용자 ID로 장바구니 조회
     */
    Optional<Cart> findByUserId(String userId);
}
