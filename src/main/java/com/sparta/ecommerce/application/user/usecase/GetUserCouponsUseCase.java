package com.sparta.ecommerce.application.user;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.CouponStatus;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 쿠폰 목록 조회 UseCase
 */
@Service
@RequiredArgsConstructor
public class GetUserCouponsUseCase {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;

    /**
     * 사용자의 쿠폰 목록 조회
     * @param userId 사용자 ID
     * @param status 쿠폰 상태 필터 (null이면 전체)
     * @return 사용자 쿠폰 목록
     */
    @Transactional(readOnly = true)
    public List<UserCouponResponse> execute(String userId, CouponStatus status) {
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);

        return userCoupons.stream()
                .filter(userCoupon -> status == null || userCoupon.getStatus() == status)
                .map(userCoupon -> {
                    Coupon coupon = couponRepository.findById(userCoupon.getCouponId())
                            .orElseThrow(() -> new InvalidCouponException("쿠폰을 찾을 수 없습니다"));
                    return UserCouponResponse.from(userCoupon, coupon);
                })
                .toList();
    }
}
