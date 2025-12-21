package com.sparta.ecommerce.infrastructure.outbox;

/**
 * Outbox 이벤트 상태
 */
public enum EventStatus {
    /**
     * 대기 중 - Kafka 발행 대기
     */
    PENDING,

    /**
     * 발행 완료 - Kafka 발행 성공
     */
    PUBLISHED,

    /**
     * 발행 실패 - 최대 재시도 횟수 초과
     */
    FAILED
}
