package com.sparta.ecommerce.application.coupon.usecase;

import com.sparta.ecommerce.application.coupon.service.CouponIssueRedisService;
import com.sparta.ecommerce.application.coupon.dto.CouponQueueResponse;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.infrastructure.aop.annotation.Trace;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import com.sparta.ecommerce.infrastructure.kafka.coupon.producer.CouponKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 유스케이스 (Kafka 방식)
 *
 * 동시성 제어 전략:
 * - Redis Set으로 중복 방지 (빠른 API 응답)
 * - Kafka Partition으로 순서 보장 + 병렬 처리
 * - 요청 즉시 응답 후 백그라운드 처리
 *
 * 흐름:
 * 1. 요청 → Redis Set 중복 체크 → Kafka 메시지 발행 → 즉시 응답
 * 2. Kafka Consumer가 Partition별로 병렬 처리
 *
 * 트랜잭션:
 * - DB 조회와 Redis 추가 간 일관성 보장
 * - 재고 확인 시점의 스냅샷 격리 수준 유지
 *
 * 변경 이력:
 * - 기존: Redis Queue (List) 사용
 * - 변경: Kafka Topic 사용 (메시지 영속성, 확장성 개선)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueCouponWithQueueUseCase {

    private final CouponRepository couponRepository;
    private final CouponIssueRedisService redisService;
    private final CouponKafkaProducer kafkaProducer;

    @Trace
    @Transactional(readOnly = true)  // DB 조회만 하므로 readOnly
    public CouponQueueResponse execute(String userId, String couponId) {
        // 0. 쿠폰 존재 여부 및 유효성 검증
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰입니다: " + couponId));

        // 쿠폰 만료 확인
        if (coupon.isExpired()) {
            throw new CouponExpiredException(couponId);
        }

        // 재고 확인 (빠른 실패)
        if (!coupon.hasStock()) {
            throw new CouponSoldOutException(couponId);
        }

        // 1. Redis Set으로 중복 체크
        Long added = redisService.addToIssuedSet(couponId, userId);

        if (added == null || added == 0) {
            // 이미 발급 요청한 경우
            throw new DuplicateCouponIssueException(couponId);
        }

        // 2. Kafka 메시지 발행
        kafkaProducer.publishCouponIssueRequest(couponId, userId);

        log.info("쿠폰 발급 요청 접수: userId={}, couponId={}, couponName={}",
                userId, couponId, coupon.getName());

        return new CouponQueueResponse(
                true,
                "쿠폰 발급 요청이 접수되었습니다. 순차적으로 처리됩니다.",
                0L  // Kafka는 실시간 큐 사이즈 조회 불가
        );
    }
}
