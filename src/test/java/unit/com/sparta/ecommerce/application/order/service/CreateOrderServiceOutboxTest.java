package com.sparta.ecommerce.application.order.service;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * CreateOrderService Outbox Pattern 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateOrderService Outbox 테스트")
class CreateOrderServiceOutboxTest {

    @Mock
    private OrderFacade orderFacade;

    @Mock
    private ProductRankingService rankingService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CreateOrderService createOrderService;

    @Test
    @DisplayName("주문 생성 시 Outbox에 이벤트가 저장된다")
    void create_savesToOutbox() throws Exception {
        // given
        String userId = "user-123";
        String couponId = "coupon-456";
        CreateOrderRequest request = new CreateOrderRequest(userId, couponId);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn("order-789");

        OrderItem orderItem = mock(OrderItem.class);
        given(orderItem.getProductId()).willReturn("product-1");

        OrderFacade.OrderResult orderResult = new OrderFacade.OrderResult(
                order,
                List.of(orderItem)
        );

        given(orderFacade.createOrder(userId, couponId)).willReturn(orderResult);
        given(objectMapper.writeValueAsString(order)).willReturn("{\"orderId\":\"order-789\"}");
        given(outboxEventRepository.save(any(OutboxEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        OrderResponse response = createOrderService.create(request);

        // then
        assertThat(response).isNotNull();

        // Outbox 저장 검증
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());

        OutboxEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getAggregateType()).isEqualTo("ORDER");
        assertThat(savedEvent.getAggregateId()).isEqualTo("order-789");
        assertThat(savedEvent.getEventType()).isEqualTo("ORDER_COMPLETED");
        assertThat(savedEvent.getPayload()).isEqualTo("{\"orderId\":\"order-789\"}");
        assertThat(savedEvent.getStatus()).isEqualTo(EventStatus.PENDING);
        assertThat(savedEvent.getRetryCount()).isEqualTo(0);
        assertThat(savedEvent.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("상품 랭킹 업데이트가 실패해도 주문은 성공하고 Outbox에 저장된다")
    void create_rankingUpdateFails_stillSavesToOutbox() throws Exception {
        // given
        String userId = "user-123";
        CreateOrderRequest request = new CreateOrderRequest(userId, null);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn("order-789");

        OrderItem orderItem = mock(OrderItem.class);
        given(orderItem.getProductId()).willReturn("product-1");

        OrderFacade.OrderResult orderResult = new OrderFacade.OrderResult(
                order,
                List.of(orderItem)
        );

        given(orderFacade.createOrder(userId, null)).willReturn(orderResult);
        given(objectMapper.writeValueAsString(order)).willReturn("{\"orderId\":\"order-789\"}");
        given(outboxEventRepository.save(any(OutboxEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

        // 랭킹 업데이트 실패
        doThrow(new RuntimeException("Redis connection failed"))
                .when(rankingService).incrementPurchaseCount(any());

        // when
        OrderResponse response = createOrderService.create(request);

        // then
        assertThat(response).isNotNull();

        // Outbox는 정상 저장되어야 함
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("JSON 직렬화 실패 시 RuntimeException이 발생한다")
    void create_jsonSerializationFails_throwsException() throws Exception {
        // given
        String userId = "user-123";
        CreateOrderRequest request = new CreateOrderRequest(userId, null);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn("order-789");

        OrderItem orderItem = mock(OrderItem.class);
        OrderFacade.OrderResult orderResult = new OrderFacade.OrderResult(
                order,
                List.of(orderItem)
        );

        given(orderFacade.createOrder(userId, null)).willReturn(orderResult);
        given(objectMapper.writeValueAsString(order))
                .willThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialization error") {});

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> createOrderService.create(request)
        );

        // Outbox 저장 시도하지 않음
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("여러 OrderItem이 있어도 하나의 Outbox 이벤트만 생성된다")
    void create_multipleOrderItems_singleOutboxEvent() throws Exception {
        // given
        String userId = "user-123";
        CreateOrderRequest request = new CreateOrderRequest(userId, null);

        Order order = mock(Order.class);
        given(order.getOrderId()).willReturn("order-789");

        OrderItem item1 = mock(OrderItem.class);
        given(item1.getProductId()).willReturn("product-1");

        OrderItem item2 = mock(OrderItem.class);
        given(item2.getProductId()).willReturn("product-2");

        OrderItem item3 = mock(OrderItem.class);
        given(item3.getProductId()).willReturn("product-3");

        OrderFacade.OrderResult orderResult = new OrderFacade.OrderResult(
                order,
                List.of(item1, item2, item3)
        );

        given(orderFacade.createOrder(userId, null)).willReturn(orderResult);
        given(objectMapper.writeValueAsString(order)).willReturn("{\"orderId\":\"order-789\"}");
        given(outboxEventRepository.save(any(OutboxEvent.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        createOrderService.create(request);

        // then
        // Outbox 이벤트는 1번만 저장
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));

        // 랭킹 업데이트는 3번 (각 상품마다)
        verify(rankingService, times(3)).incrementPurchaseCount(any());
    }
}
