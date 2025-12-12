package com.sparta.ecommerce.domain.order.event;

import com.sparta.ecommerce.domain.order.entity.Order;

/**
 * 주문 완료 이벤트
 * 주문 생성 및 결제가 완료되었을 때 발행되는 이벤트
 */
public record OrderCompletedEvent(Order order) {

}
