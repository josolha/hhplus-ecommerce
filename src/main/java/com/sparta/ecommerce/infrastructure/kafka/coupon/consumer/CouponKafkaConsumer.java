package com.sparta.ecommerce.infrastructure.kafka.coupon.consumer;

import com.sparta.ecommerce.application.coupon.service.CouponIssueProcessor;
import com.sparta.ecommerce.infrastructure.kafka.coupon.message.CouponIssueMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 Kafka Consumer
 *
 * 역할:
 * - Kafka Topic에서 쿠폰 발급 요청 메시지를 소비
 * - CouponIssueProcessor를 통해 실제 쿠폰 발급 처리
 *
 * 동시성 제어:
 * - concurrency = 3 : 3개의 Consumer 스레드 실행
 * - 각 Consumer는 서로 다른 파티션을 할당받아 병렬 처리
 * - 같은 파티션 내에서는 순차 처리 보장
 *
 * 파티션 전략:
 * - Topic: coupon-issue-request (Partition 0, 1, 2)
 * - Consumer 1 → Partition 0
 * - Consumer 2 → Partition 1
 * - Consumer 3 → Partition 2
 * - 같은 couponId는 같은 파티션으로 라우팅 → 순차 처리
 *
 * 예외 처리:
 * - CouponIssueProcessor가 예외를 처리하므로 Consumer는 메시지만 전달
 * - 처리 실패 시 Kafka 자동 재시도 (Offset 커밋 실패)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponKafkaConsumer {

    private final CouponIssueProcessor couponIssueProcessor;

    /**
     * 쿠폰 발급 메시지 소비
     *
     * @param message 쿠폰 발급 요청 메시지
     */
    @KafkaListener(
            topics = "coupon-issue-request",
            groupId = "coupon-issue-group",
            concurrency = "3"  // 파티션 수(3)와 동일하게 설정
    )
    public void consumeCouponIssueRequest(CouponIssueMessage message) {
        log.info("[Kafka Consumer] 쿠폰 발급 메시지 수신 - couponId: {}, userId: {}, requestedAt: {}",
                message.couponId(), message.userId(), message.requestedAt());

        // 쿠폰 발급 처리 (트랜잭션 내에서 실행)
        // CouponIssueProcessor가 예외 처리 및 재시도 로직 포함
        couponIssueProcessor.processSingleIssue(message.userId(), message.couponId());

        log.info("[Kafka Consumer] 쿠폰 발급 처리 완료 - couponId: {}, userId: {}",
                message.couponId(), message.userId());
    }
}
