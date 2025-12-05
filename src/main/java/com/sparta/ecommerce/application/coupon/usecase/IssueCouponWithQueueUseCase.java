package com.sparta.ecommerce.application.coupon.usecase;

import com.sparta.ecommerce.application.coupon.service.CouponQueueService;
import com.sparta.ecommerce.application.coupon.dto.CouponQueueResponse;
import com.sparta.ecommerce.common.aop.annotation.Trace;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 발급 유스케이스 (Redis 큐 방식)
 *
 * 동시성 제어 전략:
 * - Redis List 기반 FIFO 큐
 * - 분산 락 불필요 (큐 자체가 순서 보장)
 * - 요청 즉시 응답 후 백그라운드 처리
 *
 * 흐름:
 * 1. 요청 → Redis 큐에 추가 → 즉시 응답
 * 2. Worker가 주기적으로 큐에서 꺼내서 발급 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueCouponWithQueueUseCase {

    private final CouponQueueService queueService;

    @Trace
    public CouponQueueResponse execute(String userId, String couponId) {
        // 1. 큐에 추가 시도
        boolean added = queueService.addToQueue(couponId, userId);

        if (!added) {
            // 이미 발급 요청한 경우
            throw new DuplicateCouponIssueException(couponId);
        }

        // 2. 대기 순번 조회
        long queueSize = queueService.getQueueSize(couponId);

        log.info("쿠폰 발급 큐 추가 완료: userId={}, couponId={}, queueSize={}", userId, couponId, queueSize);

        return new CouponQueueResponse(
                true,
                "쿠폰 발급 요청이 접수되었습니다. 순차적으로 처리됩니다.",
                queueSize
        );
    }
}
