package com.sparta.ecommerce.infrastructure.external;

import com.sparta.ecommerce.domain.order.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ExternalDataPlatformService {
    public void sendOrderData(Order order) {

        log.info("[외부 데이터 플랫폼] 주문 정보 전송 시작 - Order ID : {} , User ID : {}, Amount : {}",
                order.getOrderId(), order.getUserId(), order.getFinalAmount());

        try {
            //Mock : 외부 API 호출 시뮬레이션 (2초 소요)
            Thread.sleep(2000);
            //Mock : 10% 확률오 실패 시뮬레이션
            if (Math.random() < 0.1) {
                throw new RuntimeException("외부 API 타임아웃 시뮬레이션");
            }
            log.info("[외부 데이터 플랫폼] 주문 정보 전송 성공 - Order ID : {}", order.getOrderId());
        }catch(InterruptedException e) {

            Thread.currentThread().interrupt();
            log.error("[외부 데이터 플랫폼] 주문 정보 전송 중단 - Order ID: {}", order.getOrderId(), e);
            throw new RuntimeException("외부 데이터 전송 실패", e);

        }catch(Exception e) {

            log.error("[외부 데이터 플랫폼] 주문 정보 전송 실패 - Order ID: {}", order.getOrderId(), e);
            throw new RuntimeException("외부 데이터 전송 실패", e);
        }
    }
}
