package com.sparta.ecommerce.application.order.listener;

import com.sparta.ecommerce.domain.order.event.OrderCompletedEvent;
import com.sparta.ecommerce.infrastructure.kafka.order.producer.OrderKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderKafkaProducer orderKafkaProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("[이벤트 리스너] 주문 완료 이벤트 수신 - Order ID: {}", event.order().getOrderId());

        // Kafka로 메시지 발행 (외부 전송은 Consumer가 처리)
        orderKafkaProducer.publishOrderCompleted(event.order());

        log.info("[이벤트 리스너] Kafka 메시지 발행 완료 - Order ID: {}", event.order().getOrderId());
    }
}
