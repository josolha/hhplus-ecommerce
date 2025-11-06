package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.Coupon;
import com.sparta.ecommerce.domain.coupon.CouponRepository;
import com.sparta.ecommerce.domain.coupon.UserCoupon;
import com.sparta.ecommerce.domain.coupon.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 쿠폰 발급 유스케이스
 */
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 발급
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰 정보
     */
    public UserCouponResponse execute(String userId, String couponId) {
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
        Coupon issuedCoupon = coupon.issue();
        couponRepository.save(issuedCoupon);

        // 6. 사용자 쿠폰 발급 이력 저장
        String userCouponId = "UC" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UserCoupon userCoupon = UserCoupon.issue(userCouponId, userId, issuedCoupon);
        userCouponRepository.save(userCoupon);

        // 7. 응답 생성
        return UserCouponResponse.from(userCoupon, issuedCoupon);
    }
}
