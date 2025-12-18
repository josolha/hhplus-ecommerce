package com.sparta.ecommerce.infrastructure.kafka.order.producer;

import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.infrastructure.kafka.order.message.OrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaProducer {

    private static final String TOPIC = "order-completed";
    private final KafkaTemplate<String, OrderCompletedMessage> kafkaTemplate;

    public void publishOrderCompleted(Order order) {
        OrderCompletedMessage message = OrderCompletedMessage.from(order);
        String key = order.getOrderId().toString();

        kafkaTemplate.send(TOPIC, key, message);

        log.info("[Kafka Producer] 주문 완료 메시지 발행 - Topic: {}, Key: {}, Order ID: {}",
                TOPIC, key, order.getOrderId());
    }
}
