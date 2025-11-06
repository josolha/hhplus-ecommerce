package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.CouponResponse;
import com.sparta.ecommerce.domain.coupon.Coupon;
import com.sparta.ecommerce.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 발급 가능한 쿠폰 목록 조회 유스케이스
 */
@Service
@RequiredArgsConstructor
public class GetAvailableCouponsUseCase {

    private final CouponRepository couponRepository;

    /**
     * 발급 가능한 쿠폰 목록 조회
     * - 재고가 남아있고
     * - 만료되지 않은 쿠폰만 반환
     */
    public List<CouponResponse> execute() {
        List<Coupon> availableCoupons = couponRepository.findAvailableCoupons();

        return availableCoupons.stream()
                .map(CouponResponse::from)
                .toList();
    }
}
