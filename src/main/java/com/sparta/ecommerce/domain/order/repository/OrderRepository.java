package com.sparta.ecommerce.domain.order.repository;

import com.sparta.ecommerce.domain.order.OrderStatus;
import com.sparta.ecommerce.domain.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 Repository 인터페이스
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * 사용자 ID로 주문 목록 조회
     */
    List<Order> findByUserId(String userId);

    /**
     * 사용자 ID로 주문 목록 조회 (페이징)
     */
    Page<Order> findByUserId(String userId, Pageable pageable);

    /**
     * 사용자 ID와 상태로 주문 목록 조회
     */
    List<Order> findByUserIdAndStatus(String userId, OrderStatus status);

    /**
     * 사용자 ID와 상태로 주문 목록 조회 (페이징)
     */
    Page<Order> findByUserIdAndStatus(String userId, OrderStatus status, Pageable pageable);

    /**
     * 특정 날짜 이후의 주문 목록 조회
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt >= :startDate")
    List<Order> findByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
}
