package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.coupon.UserCoupon;
import com.sparta.ecommerce.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 인메모리 사용자 쿠폰 저장소
 */
@Repository
@RequiredArgsConstructor
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final InMemoryDataStore dataStore;

    @Override
    public Optional<UserCoupon> findById(String userCouponId) {
        return Optional.ofNullable(dataStore.getUserCoupons().get(userCouponId));
    }

    @Override
    public List<UserCoupon> findByUserId(String userId) {
        return dataStore.getUserCoupons().values().stream()
                .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
                .toList();
    }

    @Override
    public boolean existsByUserIdAndCouponId(String userId, String couponId) {
        return dataStore.getUserCoupons().values().stream()
                .anyMatch(userCoupon ->
                        userId.equals(userCoupon.getUserId()) &&
                        couponId.equals(userCoupon.getCouponId())
                );
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        dataStore.getUserCoupons().put(userCoupon.getUserCouponId(), userCoupon);
        return userCoupon;
    }
}
