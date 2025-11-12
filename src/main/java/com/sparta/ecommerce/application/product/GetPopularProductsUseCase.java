package com.sparta.ecommerce.application.product;


import com.sparta.ecommerce.application.product.dto.ProductResponse;
import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.entity.OrderItem;
import com.sparta.ecommerce.domain.order.repository.OrderRepository;
import com.sparta.ecommerce.domain.order.repository.OrderItemRepository;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class GetPopularProductsUseCase {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public List<ProductResponse> execute(int days, int limit) {
        // 1. OrderRepository에서 최근 N일 주문 조회
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Order> recentOrders = orderRepository.findByCreatedAtAfter(startDate);

        // Early return if no orders
        if (recentOrders.isEmpty()) {
            return List.of();
        }

        // 2. 최근 주문의 ID 목록 추출
        List<String> orderIds = recentOrders.stream()
                .map(Order::getOrderId)
                .toList();

        // 3. 주문 아이템들을 한 번에 조회 (N+1 방지)
        List<OrderItem> orderItems = orderIds.stream()
                .flatMap(orderId -> orderItemRepository.findByOrderId(orderId).stream())
                .toList();

        // 4. 상품별 판매량 집계 (도메인 로직)
        Map<String, Long> salesByProduct = orderItems.stream()
                .collect(Collectors.groupingBy(
                        OrderItem::getProductId,
                        Collectors.summingLong(OrderItem::getQuantity)
                ));

        // 5. Top N 추출
        List<String> topProductIds = getTopProducts(salesByProduct, limit);

        // Early return if no products
        if (topProductIds.isEmpty()) {
            return List.of();
        }

        // 6. ProductRepository에서 상품 정보 조회
        List<Product> products = productRepository.findByProductIdIn(topProductIds);

        // 7. DTO 변환
        return products.stream()
                .map(ProductResponse::from)
                .toList();
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
