package com.sparta.ecommerce.infrastructure.kafka.coupon.producer;

import com.sparta.ecommerce.infrastructure.kafka.coupon.message.CouponIssueMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 Kafka Producer
 *
 * 역할:
 * - 쿠폰 발급 요청을 Kafka Topic으로 발행
 * - couponId를 메시지 키로 사용하여 파티션 라우팅
 *
 * 파티션 전략:
 * - 메시지 키: couponId
 * - 효과: 같은 쿠폰의 발급 요청은 항상 같은 파티션으로 라우팅
 * - 보장: 파티션 내에서 순차 처리 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponKafkaProducer {

    private static final String TOPIC = "coupon-issue-request";

    private final KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

    /**
     * 쿠폰 발급 요청 메시지 발행
     *
     * @param couponId 쿠폰 ID (메시지 키로 사용)
     * @param userId   사용자 ID
     */
    public void publishCouponIssueRequest(String couponId, String userId) {
        CouponIssueMessage message = CouponIssueMessage.of(couponId, userId);

        // couponId를 메시지 키로 사용
        // Kafka는 hash(key) % partitionCount 로 파티션 결정
        // 같은 couponId는 항상 같은 파티션으로 라우팅됨
        kafkaTemplate.send(TOPIC, couponId, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka Producer] 쿠폰 발급 메시지 발행 실패 - couponId: {}, userId: {}",
                                couponId, userId, ex);
                    } else {
                        log.info("[Kafka Producer] 쿠폰 발급 메시지 발행 성공 - couponId: {}, userId: {}, partition: {}",
                                couponId, userId, result.getRecordMetadata().partition());
                    }
                });
    }
}
