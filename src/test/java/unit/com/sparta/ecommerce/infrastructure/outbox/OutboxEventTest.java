package com.sparta.ecommerce.infrastructure.outbox;

import com.sparta.ecommerce.infrastructure.outbox.entity.OutboxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxEvent 엔티티 단위 테스트
 */
@DisplayName("OutboxEvent 엔티티 테스트")
class OutboxEventTest {

    @Test
    @DisplayName("발행 성공 시 PUBLISHED 상태로 변경되고 publishedAt이 설정된다")
    void markAsPublished() {
        // given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_COMPLETED")
                .payload("{\"orderId\":\"order-123\"}")
                .status(EventStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        // when
        event.markAsPublished();

        // then
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("발행 실패 시 FAILED 상태로 변경된다")
    void markAsFailed() {
        // given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_COMPLETED")
                .payload("{\"orderId\":\"order-123\"}")
                .status(EventStatus.PENDING)
                .retryCount(5)
                .nextRetryAt(LocalDateTime.now())
                .build();

        // when
        event.markAsFailed();

        // then
        assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);
    }

    @Test
    @DisplayName("재시도 카운트 증가 시 retryCount가 증가하고 nextRetryAt이 Exponential Backoff로 설정된다")
    void incrementRetryCount() {
        // given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_COMPLETED")
                .payload("{\"orderId\":\"order-123\"}")
                .status(EventStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        LocalDateTime before = LocalDateTime.now();

        // when
        event.incrementRetryCount("Connection timeout");

        // then
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(event.getNextRetryAt()).isAfter(before);
        // 첫 번째 재시도: 2^1 * 10 = 20초 후
        assertThat(event.getNextRetryAt()).isBefore(LocalDateTime.now().plusSeconds(25));
    }

    @Test
    @DisplayName("재시도 가능 여부 확인 - maxRetry 미만이고 PENDING 상태면 true")
    void canRetry_true() {
        // given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_COMPLETED")
                .payload("{\"orderId\":\"order-123\"}")
                .status(EventStatus.PENDING)
                .retryCount(3)
                .nextRetryAt(LocalDateTime.now())
                .build();

        // when
        boolean canRetry = event.canRetry(5);

        // then
        assertThat(canRetry).isTrue();
    }

    @Test
    @DisplayName("재시도 가능 여부 확인 - maxRetry 초과하면 false")
    void canRetry_false_maxRetryExceeded() {
        // given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_COMPLETED")
                .payload("{\"orderId\":\"order-123\"}")
                .status(EventStatus.PENDING)
                .retryCount(5)
                .nextRetryAt(LocalDateTime.now())
                .build();

        // when
        boolean canRetry = event.canRetry(5);

        // then
        assertThat(canRetry).isFalse();
    }

    @Test
    @DisplayName("재시도 가능 여부 확인 - PUBLISHED 상태면 false")
    void canRetry_false_alreadyPublished() {
        // given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_COMPLETED")
                .payload("{\"orderId\":\"order-123\"}")
                .status(EventStatus.PUBLISHED)
                .retryCount(1)
                .nextRetryAt(LocalDateTime.now())
                .build();

        // when
        boolean canRetry = event.canRetry(5);

        // then
        assertThat(canRetry).isFalse();
    }

    @Test
    @DisplayName("Exponential Backoff 검증 - 재시도 횟수에 따라 대기 시간이 지수적으로 증가한다")
    void exponentialBackoff() {
        // given
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_COMPLETED")
                .payload("{\"orderId\":\"order-123\"}")
                .status(EventStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();

        LocalDateTime now = LocalDateTime.now();

        // when & then
        // 1차 재시도: 2^1 * 10 = 20초
        event.incrementRetryCount("Error 1");
        assertThat(event.getNextRetryAt()).isBetween(
                now.plusSeconds(18),
                now.plusSeconds(22)
        );

        // 2차 재시도: 2^2 * 10 = 40초
        now = LocalDateTime.now();
        event.incrementRetryCount("Error 2");
        assertThat(event.getNextRetryAt()).isBetween(
                now.plusSeconds(38),
                now.plusSeconds(42)
        );

        // 3차 재시도: 2^3 * 10 = 80초
        now = LocalDateTime.now();
        event.incrementRetryCount("Error 3");
        assertThat(event.getNextRetryAt()).isBetween(
                now.plusSeconds(78),
                now.plusSeconds(82)
        );
    }
}
