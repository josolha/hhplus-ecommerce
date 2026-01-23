package com.sparta.ecommerce.infrastructure.outbox.publisher;

import com.sparta.ecommerce.infrastructure.outbox.EventStatus;
import com.sparta.ecommerce.infrastructure.outbox.entity.OutboxEvent;
import com.sparta.ecommerce.infrastructure.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 이벤트를 Kafka로 발행하는 Scheduler
 *
 * 주기적으로 PENDING 상태의 이벤트를 조회하여 Kafka로 발행
 * 발행 실패 시 Exponential Backoff 재시도 적용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int MAX_RETRY_COUNT = 5;
    private static final String ORDER_TOPIC = "order-events";

    /**
     * 5초마다 PENDING 이벤트를 조회하여 Kafka로 발행
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findTop100ByStatusAndNextRetryAtBefore(
                        EventStatus.PENDING,
                        LocalDateTime.now()
                );

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Outbox 이벤트 발행 시작 - 대상: {}건", pendingEvents.size());

        int successCount = 0;
        int failCount = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                publishToKafka(event);
                event.markAsPublished();
                successCount++;

                log.debug("이벤트 발행 성공 - eventId={}, aggregateId={}, eventType={}",
                        event.getId(), event.getAggregateId(), event.getEventType());

            } catch (Exception e) {
                handlePublishFailure(event, e);
                failCount++;

                log.warn("이벤트 발행 실패 - eventId={}, retryCount={}, error={}",
                        event.getId(), event.getRetryCount(), e.getMessage());
            }

            outboxEventRepository.save(event);
        }

        log.info("Outbox 이벤트 발행 완료 - 성공: {}건, 실패: {}건", successCount, failCount);
    }

    /**
     * Kafka로 이벤트 발행
     */
    private void publishToKafka(OutboxEvent event) {
        String topic = getTopicByEventType(event.getEventType());

        kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 발행 실패 - eventId={}, topic={}, error={}",
                                event.getId(), topic, ex.getMessage());
                        throw new RuntimeException("Kafka 발행 실패", ex);
                    }
                });
    }

    /**
     * 이벤트 타입에 따른 Kafka Topic 결정
     */
    private String getTopicByEventType(String eventType) {
        return switch (eventType) {
            case "ORDER_COMPLETED" -> ORDER_TOPIC;
            // 향후 확장: PAYMENT_COMPLETED, COUPON_ISSUED 등
            default -> "default-events";
        };
    }

    /**
     * 발행 실패 처리
     * - 재시도 횟수 증가
     * - 최대 재시도 초과 시 FAILED 상태로 변경
     */
    private void handlePublishFailure(OutboxEvent event, Exception e) {
        event.incrementRetryCount(e.getMessage());

        if (!event.canRetry(MAX_RETRY_COUNT)) {
            event.markAsFailed();
            log.error("이벤트 발행 최종 실패 - eventId={}, aggregateId={}, maxRetry 초과",
                    event.getId(), event.getAggregateId());
        }
    }

    /**
     * 매일 자정에 30일 이상 지난 PUBLISHED 이벤트 삭제
     * 보관 기간이 지난 성공 이벤트를 정리하여 테이블 사이즈 관리
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupOldPublishedEvents() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        outboxEventRepository.deleteOldPublishedEvents(cutoffDate);
        log.info("오래된 Outbox 이벤트 정리 완료 - cutoffDate={}", cutoffDate);
    }
}
