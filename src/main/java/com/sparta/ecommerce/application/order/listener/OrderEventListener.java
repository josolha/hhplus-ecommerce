package com.sparta.ecommerce.application.order.listener;

import com.sparta.ecommerce.domain.order.event.OrderCompletedEvent;
import com.sparta.ecommerce.infrastructure.kafka.order.producer.OrderKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * [DEPRECATED] Outbox Pattern으로 대체됨
 *
 * 기존: Spring ApplicationEvent → 즉시 Kafka 발행
 * 현재: DB Outbox 테이블 저장 → Scheduler가 비동기 발행
 *
 * 장점:
 * - 트랜잭션과 메시지 발행의 원자성 보장
 * - Kafka 장애 시에도 메시지 유실 방지
 * - 자동 재시도 메커니즘
 */
@Slf4j
//@Component  // Outbox Pattern으로 대체하여 비활성화
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
