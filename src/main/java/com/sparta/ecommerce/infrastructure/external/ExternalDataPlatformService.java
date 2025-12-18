package com.sparta.ecommerce.infrastructure.external;

import com.sparta.ecommerce.infrastructure.kafka.message.OrderCompletedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ExternalDataPlatformService {

    public void sendOrderData(OrderCompletedMessage message) {

        log.info("[외부 데이터 플랫폼] 주문 정보 전송 시작 - Order ID: {}, User ID: {}, Amount: {}",
                message.getOrderId(), message.getUserId(), message.getFinalAmount());

        try {

            // Mock: 외부 API 호출 시뮬레이션 (2초 소요)
            Thread.sleep(2000);

            // Mock: 10% 확률로 실패 시뮬레이션
            if (Math.random() < 0.1) {
                throw new RuntimeException("외부 API 타임아웃 시뮬레이션");
            }

            log.info("[외부 데이터 플랫폼] 주문 정보 전송 성공 - Order ID: {}", message.getOrderId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[외부 데이터 플랫폼] 주문 정보 전송 중단 - Order ID: {}", message.getOrderId(), e);
            throw new RuntimeException("외부 데이터 전송 실패", e);

        } catch (Exception e) {
            log.error("[외부 데이터 플랫폼] 주문 정보 전송 실패 - Order ID: {}", message.getOrderId(), e);
            throw new RuntimeException("외부 데이터 전송 실패", e);
        }
    }
}
