package com.sparta.ecommerce.application.coupon;

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
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급 트랜잭션 처리 서비스
 * IssueCouponUseCase에서 분산 락 획득 후 호출됨
 */
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 발급 비즈니스 로직 (트랜잭션)
     * 분산 락 안에서 실행되어 동시성이 보장됨
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

        // 4. 재고 확인
        if (!coupon.hasStock()) {
            throw new CouponSoldOutException(couponId);
        }

        // 5. 쿠폰 재고 차감
        couponRepository.issueCoupon(couponId);

        // 6. 사용자 쿠폰 발급
        UserCoupon userCoupon = UserCoupon.issue(userId, coupon);
        userCouponRepository.save(userCoupon);

        // 7. 응답 반환
        return UserCouponResponse.from(userCoupon, coupon);
    }
}
