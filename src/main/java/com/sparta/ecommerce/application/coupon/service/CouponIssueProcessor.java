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
        try {
            // 쿠폰 발급
            couponIssueService.issue(userId, couponId);
            log.info("쿠폰 발급 성공: userId={}, couponId={}", userId, couponId);

        } catch (CouponSoldOutException e) {
            // 재고 소진 - Set에서 제거하여 재시도 불가능하도록
            log.warn("쿠폰 재고 소진: userId={}, couponId={}", userId, couponId);
            // Set에는 그대로 두어 중복 발급 방지 유지

        } catch (DuplicateCouponIssueException e) {
            // 이미 발급됨 (정상 케이스)
            log.debug("이미 발급된 쿠폰: userId={}, couponId={}", userId, couponId);

        } catch (CouponExpiredException e) {
            // 쿠폰 만료
            log.warn("쿠폰 만료: userId={}, couponId={}", userId, couponId);
            redisService.removeFromIssuedSet(couponId, userId);

        } catch (InvalidCouponException e) {
            // 유효하지 않은 쿠폰
            log.error("유효하지 않은 쿠폰: userId={}, couponId={}", userId, couponId);
            redisService.removeFromIssuedSet(couponId, userId);

        } catch (Exception e) {
            // 기타 예외 - 재시도 가능하도록 Set에서 제거
            log.error("쿠폰 발급 실패: userId={}, couponId={}", userId, couponId, e);
            redisService.removeFromIssuedSet(couponId, userId);
        }
    }
}
