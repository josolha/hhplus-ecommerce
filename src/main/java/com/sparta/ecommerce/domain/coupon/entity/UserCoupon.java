package com.sparta.ecommerce.domain.coupon.entity;

import com.sparta.ecommerce.domain.coupon.CouponStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자별 쿠폰 발급 이력 엔티티
 */
@Entity
@Table(name = "user_coupons", indexes = {
        @Index(name = "idx_user_coupons_user_coupon", columnList = "user_id, coupon_id"),
        @Index(name = "idx_user_coupons_user_id", columnList = "user_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserCoupon {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String userCouponId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "coupon_id", nullable = false)
    private String couponId;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;      // null이면 미사용

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Version
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (this.issuedAt == null) {
            this.issuedAt = LocalDateTime.now();
        }
    }

    /**
     * 쿠폰 발급
     */
    public static UserCoupon issue(String userId, Coupon coupon) {
        return UserCoupon.builder()
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
    public void use() {
        this.usedAt = LocalDateTime.now();
    }

    public CouponStatus getStatus() {
        if(isUsed()){
            return CouponStatus.USED;
        }
        if(isExpired()){
            return CouponStatus.EXPIRED;
        }
        return CouponStatus.AVAILABLE;
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
