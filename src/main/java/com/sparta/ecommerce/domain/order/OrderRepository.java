package com.sparta.ecommerce.domain.order;

import java.util.List;
import java.util.Optional;

/**
 * 주문 Repository 인터페이스
 */
public interface OrderRepository {

    /**
     * 주문 저장
     */
    void save(Order order);

    /**
     * 주문 ID로 조회
     */
    Optional<Order> findById(String orderId);

    /**
     * 사용자 ID로 주문 목록 조회
     */
    List<Order> findByUserId(String userId);

    /**
     * 사용자 ID와 상태로 주문 목록 조회
     */
    List<Order> findByUserIdAndStatus(String userId, OrderStatus status);
}
