package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.OrderDetailResponse;
import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.entity.OrderItem;
import com.sparta.ecommerce.domain.order.repository.OrderRepository;
import com.sparta.ecommerce.domain.order.repository.OrderItemRepository;
import com.sparta.ecommerce.domain.order.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주문 상세 조회 UseCase
 */
@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 주문 상세 정보 조회
     *
     * @param orderId 주문 ID
     * @return 주문 상세 정보
     */
    public OrderDetailResponse execute(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 주문 아이템 조회
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        return OrderDetailResponse.from(order, orderItems);
    }
}
