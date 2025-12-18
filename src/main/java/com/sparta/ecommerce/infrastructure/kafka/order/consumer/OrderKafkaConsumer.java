package com.sparta.ecommerce.infrastructure.kafka.order.consumer;

import com.sparta.ecommerce.infrastructure.external.ExternalDataPlatformService;
import com.sparta.ecommerce.infrastructure.kafka.order.message.OrderCompletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaConsumer {

    private final ExternalDataPlatformService externalDataPlatformService;

    @KafkaListener(
            topics = "order-completed",
            groupId = "ecommerce-data-platform-group",
            concurrency = "3"
    )
    public void consumeOrderCompleted(OrderCompletedMessage message) {
        log.info("[Kafka Consumer] 주문 완료 메시지 수신 - Order ID: {}, User ID: {}, Amount: {}",
                message.getOrderId(), message.getUserId(), message.getFinalAmount());

        try {
            // 외부 데이터 플랫폼으로 전송 (기존 Mock API 사용)
            externalDataPlatformService.sendOrderData(message);

            log.info("[Kafka Consumer] 외부 데이터 플랫폼 전송 완료 - Order ID: {}", message.getOrderId());

        } catch (Exception e) {
            log.error("[Kafka Consumer] 외부 데이터 플랫폼 전송 실패 - Order ID: {}, 주문은 정상 처리됨",
                    message.getOrderId(), e);
            // TODO: 재시도 로직 또는 DLQ(Dead Letter Queue) 처리
        }
    }
}
