package com.sparta.ecommerce.application.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.application.product.service.ProductRankingService;
import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.entity.OrderItem;
import com.sparta.ecommerce.domain.order.service.OrderFacade;
import com.sparta.ecommerce.infrastructure.outbox.EventStatus;
import com.sparta.ecommerce.infrastructure.outbox.entity.OutboxEvent;
import com.sparta.ecommerce.infrastructure.outbox.repository.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 주문 생성 트랜잭션 처리 서비스
 * CreateOrderUseCase에서 분산 락 획득 후 호출됨
 *
 * Outbox Pattern 적용:
 * - 주문 생성과 이벤트 저장을 하나의 트랜잭션으로 처리
 * - Kafka 발행은 별도의 Scheduler가 비동기로 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderService {

    private final OrderFacade orderFacade;
    private final ProductRankingService rankingService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 주문 생성 비즈니스 로직 (트랜잭션)
     *
     * 트랜잭션 관리와 이벤트 발행을 담당
     * 분산 락 안에서 실행되어 동시성이 보장됨
     *
     * 주의: 이 메서드는 상품별 분산 락이 필요한 작업을 수행합니다.
     * OrderFacade.createOrder()는 재고 차감을 포함하므로,
     * UseCase에서 상품 ID 기반 락을 획득해야 합니다.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        // 1. 주문 생성 (모든 복잡한 로직은 Facade가 처리)
        OrderFacade.OrderResult result = orderFacade.createOrder(
                request.userId(),
                request.couponId()
        );

        // 2. 상품 랭킹 업데이트 (주문 완료 시)
        updateProductRanking(result.orderItems());

        // 3. Outbox에 이벤트 저장 (같은 트랜잭션 - 원자성 보장)
        saveToOutbox(result.order());

        // 4. 응답 생성
        return OrderResponse.from(
                result.order(),
                result.orderItems()
        );
    }

    /**
     * 상품 랭킹 업데이트 (Redis)
     * 주문 완료 시 구매된 상품의 랭킹 점수 증가
     */
    private void updateProductRanking(List<OrderItem> orderItems) {
        try {
            orderItems.forEach(item -> {
                rankingService.incrementPurchaseCount(item.getProductId());
            });
            log.debug("상품 랭킹 업데이트 완료: {} 건", orderItems.size());
        } catch (Exception e) {
            // 랭킹 업데이트 실패해도 주문은 성공해야 함
            log.error("상품 랭킹 업데이트 실패", e);
        }
    }

    /**
     * Outbox에 주문 완료 이벤트 저장
     * 주문 트랜잭션과 함께 커밋되어 원자성 보장
     */
    private void saveToOutbox(Order order) {
        try {
            // 주문 데이터를 JSON으로 직렬화
            String payload = objectMapper.writeValueAsString(order);

            // Outbox 이벤트 생성
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("ORDER")
                    .aggregateId(order.getOrderId())
                    .eventType("ORDER_COMPLETED")
                    .payload(payload)
                    .status(EventStatus.PENDING)
                    .retryCount(0)
                    .nextRetryAt(LocalDateTime.now())
                    .build();

            outboxEventRepository.save(outboxEvent);

            log.info("Outbox 이벤트 저장 완료 - orderId={}, eventId={}",
                    order.getOrderId(), outboxEvent.getId());

        } catch (JsonProcessingException e) {
            log.error("Outbox 이벤트 JSON 직렬화 실패 - orderId={}", order.getOrderId(), e);
            throw new RuntimeException("이벤트 저장 실패", e);
        }
    }
}
