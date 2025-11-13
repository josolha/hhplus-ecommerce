package com.sparta.ecommerce.application.product;


import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.domain.order.repository.OrderItemRepository;
import com.sparta.ecommerce.domain.product.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인기 상품 조회 UseCase
 *
 * [개선 사항]
 * - 기존: N+1 쿼리 문제 (Order 조회 1번 + OrderItem N번 조회 + Product 조회 1번)
 * - 개선: 단일 JOIN 쿼리로 한 번에 조회 (ORDER BY, GROUP BY, LIMIT 활용)
 * - 성능: 쿼리 수 N+2개 → 1개로 감소
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
     * @return 인기 상품 목록
     */
    public List<ProductResponse> execute(int days, int limit) {
        // 최근 N일 시작 날짜 계산
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        // Repository에서 JOIN + GROUP BY + ORDER BY + LIMIT 한 방 쿼리로 조회
        List<Product> popularProducts = orderItemRepository.findPopularProducts(startDate, limit);

        // DTO 변환
        return popularProducts.stream()
                .map(ProductResponse::from)
                .toList();
    }
}
