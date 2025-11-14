package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.OrderDetailResponse;
import com.sparta.ecommerce.domain.order.Order;
import com.sparta.ecommerce.domain.order.OrderRepository;
import com.sparta.ecommerce.domain.order.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 상세 조회 UseCase
 */
@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase {

    private final OrderRepository orderRepository;

    /**
     * 주문 상세 정보 조회
     *
     * @param orderId 주문 ID
     * @return 주문 상세 정보
     */
    public OrderDetailResponse execute(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return OrderDetailResponse.from(order);
    }
}
