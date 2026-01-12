package com.sparta.ecommerce.application.coupon.service;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 발급 트랜잭션 처리 서비스
 * Kafka Consumer (CouponIssueProcessor)에서 순차적으로 호출됨
 *
 * 동시성 제어:
 * - Kafka Consumer가 메시지를 순차 처리 (동시 호출 없음)
 * - 원자적 UPDATE 쿼리로 재고 차감 (DB 레벨 동시성 보장)
 * - 분산락 불필요 (Kafka가 이미 순서 보장)
 */
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 발급 비즈니스 로직
     * Kafka Consumer가 순차 처리하므로 동시성 문제 없음
     */
    public UserCouponResponse issue(String userId, String couponId) {
        // 1. 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰입니다"));

        // 2. 중복 발급 체크
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new DuplicateCouponIssueException(couponId);
        }

        // 3. 만료 확인
        if (coupon.isExpired()) {
            throw new CouponExpiredException(couponId);
        }

        // 4. 쿠폰 재고 차감 (원자적 UPDATE - 재고 있을 때만 차감)
        int updated = couponRepository.issueCoupon(couponId);
        if (updated == 0) {
            // UPDATE 실패 = 재고 부족 (음수 재고 방지)
            throw new CouponSoldOutException(couponId);
        }

        // 5. 사용자 쿠폰 발급
        UserCoupon userCoupon = UserCoupon.issue(userId, coupon);
        userCouponRepository.save(userCoupon);

        // 7. 응답 반환
        return UserCouponResponse.from(userCoupon, coupon);
    }
}
