package com.sparta.ecommerce.domain.order.repository;

import com.sparta.ecommerce.application.product.dto.PopularProductResponse;
import com.sparta.ecommerce.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 항목 Repository 인터페이스
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

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
     * - DTO Projection으로 필요한 데이터만 조회
     * - JOIN으로 한 번에 조회
     * - GROUP BY로 상품별 판매량 집계
     * - ORDER BY로 판매량 내림차순 정렬
     * - LIMIT으로 상위 N개만 조회
     *
     * @param startDate 조회 시작 날짜 (최근 N일 계산)
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록 (판매량 포함)
     *
     *  1순위: 판매량
     *  2순위: 최근에 더 많이 팔린 상품
     *  3순위: 상품 생성일 (최근 등록 상품 우선)
     */
    @Query("""
        SELECT new com.sparta.ecommerce.application.product.dto.PopularProductResponse(
            p.productId,
            p.name,
            p.price,
            p.stock.quantity,
            p.category,
            SUM(oi.quantity)
        )
        FROM OrderItem oi
        JOIN Order o ON oi.orderId = o.orderId
        JOIN Product p ON oi.productId = p.productId
        WHERE o.createdAt >= :startDate
          AND o.status = 'COMPLETED'
        GROUP BY p.productId, p.name, p.price, p.stock.quantity, p.category, p.createdAt
        ORDER BY SUM(oi.quantity) DESC, MAX(o.createdAt) DESC, p.createdAt DESC
        LIMIT :limit
        """)
    List<PopularProductResponse> findPopularProducts(@Param("startDate") LocalDateTime startDate,
                                                       @Param("limit") int limit);
}
