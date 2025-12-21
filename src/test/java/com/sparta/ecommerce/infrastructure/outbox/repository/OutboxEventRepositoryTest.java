package com.sparta.ecommerce.infrastructure.outbox.repository;

import com.sparta.ecommerce.IntegrationTestBase;
import com.sparta.ecommerce.infrastructure.outbox.EventStatus;
import com.sparta.ecommerce.infrastructure.outbox.entity.OutboxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxEventRepository 통합 테스트
 */
@DisplayName("OutboxEventRepository 테스트")
class OutboxEventRepositoryTest extends IntegrationTestBase {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("PENDING 상태이고 재시도 시간이 된 이벤트를 조회한다")
    void findTop100ByStatusAndNextRetryAtBefore() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // 재시도 시간이 지난 이벤트 (조회 대상)
        OutboxEvent event1 = createOutboxEvent("order-1", EventStatus.PENDING, now.minusMinutes(5));
        OutboxEvent event2 = createOutboxEvent("order-2", EventStatus.PENDING, now.minusMinutes(1));

        // 재시도 시간이 아직 안 된 이벤트 (조회 제외)
        OutboxEvent event3 = createOutboxEvent("order-3", EventStatus.PENDING, now.plusMinutes(5));

        // PUBLISHED 상태 (조회 제외)
        OutboxEvent event4 = createOutboxEvent("order-4", EventStatus.PUBLISHED, now.minusMinutes(5));

        outboxEventRepository.saveAll(List.of(event1, event2, event3, event4));

        // when
        List<OutboxEvent> result = outboxEventRepository
                .findTop100ByStatusAndNextRetryAtBefore(EventStatus.PENDING, now);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder("order-1", "order-2");
    }

    @Test
    @DisplayName("특정 Aggregate의 이벤트를 생성 시간 역순으로 조회한다")
    void findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc() {
        // given
        LocalDateTime now = LocalDateTime.now();

        OutboxEvent event1 = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_COMPLETED")
                .payload("{}")
                .status(EventStatus.PUBLISHED)
                .retryCount(0)
                .nextRetryAt(now)
                .createdAt(now.minusHours(2))
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-123")
                .eventType("ORDER_CANCELLED")
                .payload("{}")
                .status(EventStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(now)
                .createdAt(now.minusHours(1))
                .build();

        OutboxEvent event3 = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-456")
                .eventType("ORDER_COMPLETED")
                .payload("{}")
                .status(EventStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(now)
                .createdAt(now)
                .build();

        outboxEventRepository.saveAll(List.of(event1, event2, event3));

        // when
        List<OutboxEvent> result = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc("ORDER", "order-123");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEventType()).isEqualTo("ORDER_CANCELLED"); // 최신순
        assertThat(result.get(1).getEventType()).isEqualTo("ORDER_COMPLETED");
    }

    @Test
    @DisplayName("실패한 이벤트를 생성 시간 역순으로 조회한다")
    void findByStatusOrderByCreatedAtDesc() {
        // given
        LocalDateTime now = LocalDateTime.now();

        OutboxEvent event1 = createOutboxEvent("order-1", EventStatus.FAILED, now);
        OutboxEvent event2 = createOutboxEvent("order-2", EventStatus.PENDING, now);
        OutboxEvent event3 = createOutboxEvent("order-3", EventStatus.FAILED, now);

        outboxEventRepository.saveAll(List.of(event1, event2, event3));

        // when
        List<OutboxEvent> result = outboxEventRepository
                .findByStatusOrderByCreatedAtDesc(EventStatus.FAILED);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(OutboxEvent::getAggregateId)
                .containsExactly("order-3", "order-1"); // 역순
    }

    @Test
    @DisplayName("재시도 시간이 지난 순서대로 조회된다 (FIFO)")
    void findPendingEvents_orderedByCreatedAt() {
        // given
        LocalDateTime now = LocalDateTime.now();

        OutboxEvent event1 = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-1")
                .eventType("ORDER_COMPLETED")
                .payload("{}")
                .status(EventStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(now.minusMinutes(10))
                .createdAt(now.minusMinutes(10))
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId("order-2")
                .eventType("ORDER_COMPLETED")
                .payload("{}")
                .status(EventStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(now.minusMinutes(5))
                .createdAt(now.minusMinutes(5))
                .build();

        outboxEventRepository.saveAll(List.of(event2, event1)); // 순서 바꿔서 저장

        // when
        List<OutboxEvent> result = outboxEventRepository
                .findTop100ByStatusAndNextRetryAtBefore(EventStatus.PENDING, now);

        // then
        assertThat(result).hasSize(2);
        // createdAt 기준 오름차순 (먼저 생성된 것부터)
        assertThat(result.get(0).getAggregateId()).isEqualTo("order-1");
        assertThat(result.get(1).getAggregateId()).isEqualTo("order-2");
    }

    private OutboxEvent createOutboxEvent(String aggregateId, EventStatus status, LocalDateTime nextRetryAt) {
        return OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(aggregateId)
                .eventType("ORDER_COMPLETED")
                .payload("{\"orderId\":\"" + aggregateId + "\"}")
                .status(status)
                .retryCount(0)
                .nextRetryAt(nextRetryAt)
                .build();
    }
}
