package com.sparta.ecommerce.domain.coupon;

import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 저장소 인터페이스
 */
public interface CouponRepository {
    /**
     * ID로 쿠폰 조회
     */
    Optional<Coupon> findById(String couponId);

    /**
     * 모든 쿠폰 조회
     */
    List<Coupon> findAll();

    /**
     * 발급 가능한 쿠폰 조회 (재고 있고 만료되지 않은 쿠폰)
     */
    List<Coupon> findAvailableCoupons();

    /**
     * 쿠폰 저장
     */
    void save(Coupon coupon);
}
