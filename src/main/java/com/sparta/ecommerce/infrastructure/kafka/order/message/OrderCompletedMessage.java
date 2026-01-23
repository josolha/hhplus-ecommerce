package com.sparta.ecommerce.infrastructure.kafka.order.message;

import com.sparta.ecommerce.domain.order.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCompletedMessage {

    private String orderId;
    private String userId;
    private long finalAmount;
    private LocalDateTime createdAt;

    public static OrderCompletedMessage from(Order order) {
        return new OrderCompletedMessage(
                order.getOrderId(),
                order.getUserId(),
                order.getFinalAmount(),
                order.getCreatedAt()
        );
    }
}
