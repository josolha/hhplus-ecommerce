package com.sparta.ecommerce.application.coupon.service;

import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 처리 서비스 (트랜잭션 전용)
 * Kafka Consumer에서 호출하여 트랜잭션 컨텍스트에서 쿠폰을 발급합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private final CouponIssueService couponIssueService;
    private final CouponIssueRedisService redisService;

    /**
     * 단일 쿠폰 발급 처리 (트랜잭션 내에서 실행)
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     */
    @Transactional
    public void processSingleIssue(String userId, String couponId) {
        // 재고 소진 플래그 확인 (빠른 종료)
        if (redisService.isSoldOut(couponId)) {
            log.info("이미 재고 소진된 쿠폰 - 처리 스킵: userId={}, couponId={}", userId, couponId);
            return;
        }

        try {
            // 쿠폰 발급
            couponIssueService.issue(userId, couponId);
            log.info("쿠폰 발급 성공: userId={}, couponId={}", userId, couponId);

        } catch (CouponSoldOutException e) {
            // 재고 소진 - Redis 플래그 설정하여 후속 요청 빠르게 차단
            log.warn("쿠폰 재고 소진: userId={}, couponId={}", userId, couponId);

            // 재고 소진 플래그 설정 (다음 요청부터 빠른 차단)
            redisService.setSoldOutFlag(couponId);

            // Redis 재고 복구 (API에서 감소했지만 DB 저장 실패)
            redisService.incrementStock(couponId);

        } catch (DuplicateCouponIssueException e) {
            // 이미 발급됨 (Redis-DB 불일치 케이스)
            log.debug("이미 발급된 쿠폰: userId={}, couponId={}", userId, couponId);

            // Redis Set에서 제거 (재시도 가능하도록)
            redisService.removeFromIssuedSet(couponId, userId);

            // Redis 재고 복구 (중복이므로 실제 발급 안 됨)
            redisService.incrementStock(couponId);

        } catch (CouponExpiredException e) {
            // 쿠폰 만료
            log.warn("쿠폰 만료: userId={}, couponId={}", userId, couponId);
            redisService.removeFromIssuedSet(couponId, userId);
            redisService.incrementStock(couponId);

        } catch (InvalidCouponException e) {
            // 유효하지 않은 쿠폰
            log.error("유효하지 않은 쿠폰: userId={}, couponId={}", userId, couponId);
            redisService.removeFromIssuedSet(couponId, userId);
            redisService.incrementStock(couponId);

        } catch (Exception e) {
            // 기타 예외 - 재시도 가능하도록 Set에서 제거 및 재고 복구
            log.error("쿠폰 발급 실패: userId={}, couponId={}", userId, couponId, e);
            redisService.removeFromIssuedSet(couponId, userId);
            redisService.incrementStock(couponId);
        }
    }
}
