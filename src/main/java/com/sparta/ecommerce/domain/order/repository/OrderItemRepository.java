package com.sparta.ecommerce.domain.order.repository;

import com.sparta.ecommerce.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 주문 항목 Repository 인터페이스
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

    /**
     * 주문 ID로 주문 항목 목록 조회
     */
    List<OrderItem> findByOrderId(String orderId);

    /**
     * 상품 ID로 주문 항목 목록 조회 (인기 상품 분석용)
     */
    List<OrderItem> findByProductId(String productId);
}
