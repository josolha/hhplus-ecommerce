package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.event.OrderCompletedEvent;
import com.sparta.ecommerce.domain.order.repository.OrderRepository;
import com.sparta.ecommerce.infrastructure.external.ExternalDataPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final ExternalDataPlatformService externalDataPlatformService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("[이벤트 리스너] 주문 완료 이벤트 수신 - Order ID: {}", event.order().getOrderId());

        try {
            // Order 엔티티를 직접 받아서 DB 조회 불필요
            externalDataPlatformService.sendOrderData(event.order());

            log.info("[이벤트 리스너] 외부 데이터 전송 완료 - Order ID: {}", event.order().getOrderId());

        } catch (Exception e) {
            log.error("[이벤트 리스너] 외부 데이터 전송 실패 - Order ID: {}, 주문은 정상 처리됨",
                    event.order().getOrderId(), e);
        }
    }
}
