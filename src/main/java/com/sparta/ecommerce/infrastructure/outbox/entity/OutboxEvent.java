package com.sparta.ecommerce.infrastructure.outbox.entity;

import com.sparta.ecommerce.infrastructure.outbox.EventStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Outbox Pattern 이벤트 엔티티
 * 트랜잭션과 메시지 발행의 원자성을 보장
 */
@Entity
@Table(name = "event_outbox", indexes = {
        @Index(name = "idx_outbox_status_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;  // ORDER, PAYMENT

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;    // 주문ID, 결제ID 등

    @Column(name = "event_type", nullable = false)
    private String eventType;      // ORDER_COMPLETED, PAYMENT_COMPLETED

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;        // JSON 형태의 이벤트 데이터

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status;    // PENDING, PUBLISHED, FAILED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        if (this.status == null) {
            this.status = EventStatus.PENDING;
        }
        if (this.nextRetryAt == null) {
            this.nextRetryAt = LocalDateTime.now();
        }
    }

    /**
     * 발행 성공 처리
     */
    public void markAsPublished() {
        this.status = EventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * 발행 실패 처리
     */
    public void markAsFailed() {
        this.status = EventStatus.FAILED;
    }

    /**
     * 재시도 카운트 증가 및 다음 재시도 시간 설정
     * Exponential Backoff: 10초 → 30초 → 1분 → 5분 → 10분
     */
    public void incrementRetryCount(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;

        // Exponential Backoff 계산
        long delaySec = (long) Math.pow(2, this.retryCount) * 10;
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySec);
    }

    /**
     * 재시도 가능 여부 확인
     */
    public boolean canRetry(int maxRetryCount) {
        return this.retryCount < maxRetryCount && this.status == EventStatus.PENDING;
    }
}
