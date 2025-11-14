package com.sparta.ecommerce.domain.cart.repository;

import com.sparta.ecommerce.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * 장바구니 항목 Repository 인터페이스
 */
public interface CartItemRepository extends JpaRepository<CartItem, String> {

    /**
     * 장바구니 ID로 항목 목록 조회
     */
    List<CartItem> findByCartId(String cartId);

    /**
     * 장바구니 ID와 상품 ID로 항목 조회 (중복 체크용)
     */
    Optional<CartItem> findByCartIdAndProductId(String cartId, String productId);

    /**
     * 장바구니의 모든 항목 삭제 (장바구니 비우기)
     */
    void deleteByCartId(String cartId);
}
