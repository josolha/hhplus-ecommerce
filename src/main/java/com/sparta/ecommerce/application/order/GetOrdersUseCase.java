package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.OrderListResponse;
import com.sparta.ecommerce.application.order.dto.OrderSummaryResponse;
import com.sparta.ecommerce.domain.order.Order;
import com.sparta.ecommerce.domain.order.OrderRepository;
import com.sparta.ecommerce.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주문 목록 조회 UseCase
 */
@Service
@RequiredArgsConstructor
public class GetOrdersUseCase {

    private final OrderRepository orderRepository;

    /**
     * 사용자의 주문 목록 조회 (페이징)
     *
     * @param userId 사용자 ID
     * @param page   페이지 번호 (1부터 시작)
     * @param limit  페이지당 항목 수
     * @param status 주문 상태 필터 (optional)
     * @return 페이징된 주문 목록
     */
    public OrderListResponse execute(String userId, int page, int limit, String status) {
        // 주문 조회
        List<Order> orders;
        if (status != null && !status.isEmpty()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            orders = orderRepository.findByUserIdAndStatus(userId, orderStatus);
        } else {
            orders = orderRepository.findByUserId(userId);
        }

        // 페이징 계산
        int totalCount = orders.size();
        int totalPages = (int) Math.ceil((double) totalCount / limit);
        int startIndex = (page - 1) * limit;
        int endIndex = Math.min(startIndex + limit, totalCount);

        // 현재 페이지 데이터 추출
        List<OrderSummaryResponse> orderSummaries = orders.stream()
                .skip(startIndex)
                .limit(limit)
                .map(OrderSummaryResponse::from)
                .toList();

        return new OrderListResponse(
                orderSummaries,
                page,
                totalPages,
                totalCount
        );
    }
}
