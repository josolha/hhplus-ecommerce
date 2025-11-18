package com.sparta.ecommerce.domain.order.service;

import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.exception.CouponAlreadyUsedException;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 할인 계산 서비스
 *
 * 쿠폰을 이용한 할인 금액 계산을 담당하는 도메인 서비스
 */
@Service
@RequiredArgsConstructor
public class OrderDiscountCalculator {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 할인 금액 계산
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (nullable)
     * @param totalAmount 총 주문 금액
     * @return 할인 금액
     */
    public long calculate(String userId, String couponId, long totalAmount) {
        if (couponId == null || couponId.isEmpty()) {
            return 0;
        }

        // 쿠폰 조회
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰입니다"));

        // UserCoupon 조회
        UserCoupon userCoupon = userCouponRepository.findByUserId(userId).stream()
                .filter(uc -> uc.getCouponId().equals(couponId))
                .findFirst()
                .orElseThrow(() -> new InvalidCouponException("발급받지 않은 쿠폰입니다"));

        // 쿠폰 유효성 검증
        if (userCoupon.isUsed()) {
            throw new CouponAlreadyUsedException(couponId);
        }
        if (userCoupon.isExpired()) {
            throw new CouponExpiredException(couponId);
        }

        // 할인 금액 계산
        return coupon.calculateDiscountAmount((int) totalAmount);
    }
}
