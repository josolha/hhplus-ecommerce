package com.sparta.ecommerce.domain.coupon;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 쿠폰 저장소 인터페이스
 */
public interface UserCouponRepository {
    /**
     * ID로 사용자 쿠폰 조회
     */
    Optional<UserCoupon> findById(String userCouponId);

    /**
     * 사용자 ID로 쿠폰 목록 조회
     */
    List<UserCoupon> findByUserId(String userId);

    /**
     * 사용자가 특정 쿠폰을 이미 발급받았는지 확인
     */
    boolean existsByUserIdAndCouponId(String userId, String couponId);

    /**
     * 사용자 쿠폰 저장
     */
    UserCoupon save(UserCoupon userCoupon);
}
