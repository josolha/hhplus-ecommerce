package com.sparta.ecommerce.application.product;


import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.domain.order.Order;
import com.sparta.ecommerce.domain.order.OrderItem;
import com.sparta.ecommerce.domain.order.OrderRepository;
import com.sparta.ecommerce.domain.product.Product;
import com.sparta.ecommerce.domain.product.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class GetPopularProductsUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public List<ProductResponse> execute(int days, int limit) {
        // 1. OrderRepository에서 최근 N일 주문 조회
        List<Order> recentOrders = orderRepository.findRecentOrders(days);

        // 2. 상품별 판매량 집계 (도메인 로직)
        Map<String, Long> salesByProduct = aggregateSales(recentOrders);

        // 3. Top N 추출
        List<String> topProductIds = getTopProducts(salesByProduct, limit);

        // Early return if no products
        if (topProductIds.isEmpty()) {
            return List.of();
        }

        // 4. ProductRepository에서 상품 정보 조회
        List<Product> products = productRepository.findByIds(topProductIds);

        // 5. DTO 변환
        return products.stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * 주문 목록에서 상품별 판매량을 집계
     */
    private Map<String, Long> aggregateSales(List<Order> orders) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.groupingBy(
                        OrderItem::getProductId,
                        Collectors.summingLong(OrderItem::getQuantity)
                ));
    }

    /**
     * 판매량 기준 상위 N개 상품 ID 추출
     */
    private List<String> getTopProducts(Map<String, Long> salesByProduct, int limit) {
        return salesByProduct.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
