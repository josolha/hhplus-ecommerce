package com.sparta.ecommerce.domain.order;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private String orderId;
    private String userId;
    private List<OrderItem> items;
    private long totalAmount;
    private long discountAmount;
    private long finalAmount;
    private String couponId;
    private OrderStatus status;
    private LocalDateTime createdAt;


}
