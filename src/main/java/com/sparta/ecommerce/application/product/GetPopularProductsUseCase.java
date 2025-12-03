package com.sparta.ecommerce.application.product;


import com.sparta.ecommerce.application.product.dto.PopularProductResponse;
import com.sparta.ecommerce.domain.order.repository.OrderItemRepository;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 인기 상품 조회 UseCase (Redis 실시간 랭킹 기반)
 *
 * [개선 사항 - Step 13]
 * - Redis Sorted Set 기반 실시간 랭킹
 * - DB 집계 쿼리 부하 제거
 * - 조회 속도 50~100배 향상 (ms 단위)
 * - Fallback: Redis 데이터 없으면 DB 집계 쿼리로 대체
 *
 * [기존 방식 - Step 12]
 * - DB JOIN + GROUP BY + ORDER BY 집계 쿼리
 * - 캐시 TTL 5분
 * - 비교 테스트를 위해 executeWithCache() 메서드로 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetPopularProductsUseCase {

    private final ProductRankingService rankingService;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 인기 상품 조회 (Redis 실시간 랭킹)
     *
     * @param days 조회 기간 (최근 N일)
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록
     */
    @Transactional(readOnly = true)
    public List<PopularProductResponse> execute(int days, int limit) {
        try {
            // 1. Redis에서 최근 N일 Top 상품 ID와 점수 조회
            List<ProductRankingService.RankingItem> rankingItems =
                    rankingService.getTopProductsWithScore(days, limit);

            log.info("Redis 조회 결과: {} 건", rankingItems.size());
            rankingItems.forEach(item ->
                log.info("  - productId: {}, score: {}", item.productId(), item.score())
            );

            // 2. Redis에 데이터 없으면 DB Fallback
            if (rankingItems.isEmpty()) {
                log.warn("Redis에 랭킹 데이터 없음. DB 조회로 fallback");
                return executeWithDatabase(days, limit);
            }

            // 3. DB에서 상품 정보 조회 (PK 조회, 매우 빠름)
            List<String> productIdStrings = rankingItems.stream()
                    .map(ProductRankingService.RankingItem::productId)
                    .collect(Collectors.toList());

            log.info("DB 조회할 상품 ID: {}", productIdStrings);

            List<Product> products = productRepository.findAllById(productIdStrings);
            log.info("DB 조회 결과: {} 건", products.size());
            products.forEach(p -> log.info("  - Product ID: {}, Name: {}", p.getProductId(), p.getName()));

            // 4. 상품 정보 Map 생성
            Map<String, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getProductId, p -> p));

            // 5. 점수 정보 Map 생성
            Map<String, Long> scoreMap = rankingItems.stream()
                    .collect(Collectors.toMap(
                            ProductRankingService.RankingItem::productId,
                            ProductRankingService.RankingItem::score
                    ));

            // 6. Redis 순서 유지하며 Response 변환 (점수 포함)
            List<PopularProductResponse> result = rankingItems.stream()
                    .map(item -> {
                        Product product = productMap.get(item.productId());

                        if (product == null) {
                            log.warn("상품을 찾을 수 없음: productId={}", item.productId());
                            return null;
                        }

                        return new PopularProductResponse(
                                product.getProductId(),
                                product.getName(),
                                product.getPrice(),
                                product.getStock().getQuantity(),
                                product.getCategory(),
                                scoreMap.get(item.productId())  // Redis score = 판매량
                        );
                    })
                    .filter(response -> response != null)
                    .collect(Collectors.toList());

            log.info("최종 반환 결과: {} 건", result.size());
            return result;

        } catch (Exception e) {
            log.error("Redis 조회 실패. DB로 fallback", e);
            return executeWithDatabase(days, limit);
        }
    }

    /**
     * 인기 상품 조회 (DB 집계 쿼리 - Fallback 용도)
     * 기존 Step 12 방식 유지 (속도 비교 테스트용)
     */
    @Transactional(readOnly = true)
    public List<PopularProductResponse> executeWithDatabase(int days, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return orderItemRepository.findPopularProducts(startDate, limit);
    }
}
