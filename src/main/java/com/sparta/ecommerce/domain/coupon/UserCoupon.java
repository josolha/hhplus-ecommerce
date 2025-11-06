package com.sparta.ecommerce.domain.coupon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자별 쿠폰 발급 이력 엔티티
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon {
    private String userCouponId;
    private String userId;
    private String couponId;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;      // null이면 미사용
    private LocalDateTime expiresAt;

    /**
     * 쿠폰 발급
     */
    public static UserCoupon issue(String userCouponId, String userId, Coupon coupon) {
        return UserCoupon.builder()
                .userCouponId(userCouponId)
                .userId(userId)
                .couponId(coupon.getCouponId())
                .issuedAt(LocalDateTime.now())
                .usedAt(null)
                .expiresAt(coupon.getExpiresAt())
                .build();
    }

    /**
     * 쿠폰 사용 처리
     */
    public UserCoupon use() {
        return UserCoupon.builder()
                .userCouponId(this.userCouponId)
                .userId(this.userId)
                .couponId(this.couponId)
                .issuedAt(this.issuedAt)
                .usedAt(LocalDateTime.now())
                .expiresAt(this.expiresAt)
                .build();
    }

    /**
     * 사용 여부 확인
     */
    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
