package com.sparta.ecommerce.application.product;


import com.sparta.ecommerce.application.product.dto.PopularProductResponse;
import com.sparta.ecommerce.domain.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인기 상품 조회 UseCase
 *
 * [개선 사항]
 * - 기존: N+1 쿼리 문제 (Order 조회 1번 + OrderItem N번 조회 + Product 조회 1번)
 * - 개선: 단일 JOIN 쿼리로 한 번에 조회 (ORDER BY, GROUP BY, LIMIT 활용)
 * - 성능: 쿼리 수 N+2개 → 1개로 감소
 * - DTO Projection: 필요한 데이터만 조회 (판매량 포함)
 */
@Service
@RequiredArgsConstructor
public class GetPopularProductsUseCase {

    private final OrderItemRepository orderItemRepository;

    /**
     * 인기 상품 목록 조회
     *
     * @param days 조회 기간 (최근 N일)
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록 (판매량 포함)
     */
    @Transactional(readOnly = true)
    public List<PopularProductResponse> execute(int days, int limit) {
        // 최근 N일 시작 날짜 계산
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        // Repository에서 DTO Projection으로 판매량 포함하여 조회
        // JOIN + GROUP BY + ORDER BY + LIMIT 한 방 쿼리
        return orderItemRepository.findPopularProducts(startDate, limit);
    }
}
