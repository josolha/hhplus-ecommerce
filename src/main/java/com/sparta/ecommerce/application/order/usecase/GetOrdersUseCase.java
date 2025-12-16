package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.OrderListResponse;
import com.sparta.ecommerce.application.order.dto.OrderSummaryResponse;
import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.repository.OrderRepository;
import com.sparta.ecommerce.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(readOnly = true)
    public OrderListResponse execute(String userId, int page, int limit, String status) {
        // Pageable 생성 (page는 0부터 시작하므로 -1, 최신순 정렬)
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by("createdAt").descending());

        // DB에서 페이징 처리된 데이터 조회
        Page<Order> orderPage;
        if (status != null && !status.isEmpty()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            orderPage = orderRepository.findByUserIdAndStatus(userId, orderStatus, pageable);
        } else {
            orderPage = orderRepository.findByUserId(userId, pageable);
        }

        // DTO 변환
        List<OrderSummaryResponse> orderSummaries = orderPage.getContent().stream()
                .map(OrderSummaryResponse::from)
                .toList();

        return new OrderListResponse(
                orderSummaries,
                page,
                orderPage.getTotalPages(),
                (int) orderPage.getTotalElements()
        );
    }
}
