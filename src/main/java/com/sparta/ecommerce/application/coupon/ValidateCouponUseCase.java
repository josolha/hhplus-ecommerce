package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.ValidateCouponRequest;
import com.sparta.ecommerce.application.coupon.dto.ValidateCouponResponse;
import com.sparta.ecommerce.domain.coupon.Coupon;
import com.sparta.ecommerce.domain.coupon.CouponRepository;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 유효성 검증 유스케이스
 */
@Service
@RequiredArgsConstructor
public class ValidateCouponUseCase {

    private final CouponRepository couponRepository;

    /**
     * 쿠폰 유효성 검증 및 할인 금액 계산
     * @param request 쿠폰 ID와 주문 금액
     * @return 유효성 검증 결과
     */
    public ValidateCouponResponse execute(ValidateCouponRequest request) {
        // 1. 쿠폰 조회
        Coupon coupon = couponRepository.findById(request.couponId())
                .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰입니다"));

        // 2. 만료 확인
        if (coupon.isExpired()) {
            return ValidateCouponResponse.invalid(
                    "만료된 쿠폰입니다 (만료일: " + coupon.getExpiresAt() + ")"
            );
        }

        // 3. 최소 주문 금액 확인
        if (!coupon.meetsMinOrderAmount(request.orderAmount())) {
            return ValidateCouponResponse.invalid(
                    "최소 주문 금액을 만족하지 않습니다 (최소: " +
                    coupon.getMinOrderAmount() + "원)"
            );
        }

        // 4. 할인 금액 계산
        int discountAmount = coupon.calculateDiscountAmount(request.orderAmount());

        // 5. 검증 성공 응답
        return ValidateCouponResponse.valid(request.orderAmount(), discountAmount);
    }
}
