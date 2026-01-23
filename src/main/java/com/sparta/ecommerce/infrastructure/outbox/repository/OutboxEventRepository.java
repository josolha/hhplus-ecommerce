package com.sparta.ecommerce.infrastructure.outbox.repository;

import com.sparta.ecommerce.infrastructure.outbox.EventStatus;
import com.sparta.ecommerce.infrastructure.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 이벤트 Repository
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 발행 대기 중이고 재시도 시간이 된 이벤트 조회
     * Scheduler가 주기적으로 호출하여 Kafka로 발행
     *
     * @param status 이벤트 상태 (PENDING)
     * @param now 현재 시간
     * @return 발행 대기 이벤트 목록 (최대 100개)
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status AND e.nextRetryAt <= :now ORDER BY e.createdAt ASC")
    List<OutboxEvent> findTop100ByStatusAndNextRetryAtBefore(
            @Param("status") EventStatus status,
            @Param("now") LocalDateTime now
    );

    /**
     * 특정 Aggregate의 이벤트 조회 (디버깅용)
     */
    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            String aggregateType,
            String aggregateId
    );

    /**
     * 실패한 이벤트 조회 (모니터링용)
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtDesc(EventStatus status);

    /**
     * 오래된 PUBLISHED 이벤트 삭제 (배치 정리용)
     * 보관 기간이 지난 성공 이벤트를 주기적으로 삭제
     */
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :cutoffDate")
    void deleteOldPublishedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);
}
