package com.sparta.ecommerce.domain.order.repository;

import com.sparta.ecommerce.domain.order.entity.OrderItem;
import com.sparta.ecommerce.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * 인기 상품 조회 (최근 N일간 판매량 기준 Top N)
     * - JOIN으로 한 번에 조회
     * - GROUP BY로 상품별 판매량 집계
     * - ORDER BY로 판매량 내림차순 정렬
     * - LIMIT으로 상위 N개만 조회
     *
     * @param startDate 조회 시작 날짜 (최근 N일 계산)
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록
     */
    @Query("""
        SELECT p
        FROM OrderItem oi
        JOIN Order o ON oi.orderId = o.orderId
        JOIN Product p ON oi.productId = p.productId
        WHERE o.createdAt >= :startDate
        GROUP BY p.productId, p.name, p.description, p.price, p.stock, p.category, p.createdAt, p.updatedAt
        ORDER BY SUM(oi.quantity) DESC
        LIMIT :limit
        """)
    List<Product> findPopularProducts(@Param("startDate") LocalDateTime startDate,
                                       @Param("limit") int limit);
}
