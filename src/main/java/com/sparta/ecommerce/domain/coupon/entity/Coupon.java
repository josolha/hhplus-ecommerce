package com.sparta.ecommerce.domain.coupon.entity;

import com.sparta.ecommerce.domain.common.BaseEntity;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 엔티티
 */
@Entity
@Table(name = "coupons")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Coupon extends BaseEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String couponId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Embedded
    private CouponStock stock;

    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 발급 가능한 쿠폰인지 확인
     */
    public boolean isAvailable() {
        return hasStock() && !isExpired();
    }

    /**
     * 재고가 남아있는지 확인
     */
    public boolean hasStock() {
        return stock.hasStock();
    }

    /**
     * 만료되었는지 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 최소 주문 금액을 만족하는지 확인
     */
    public boolean meetsMinOrderAmount(long orderAmount) {
        return orderAmount >= minOrderAmount;
    }

    /**
     * 할인 금액 계산
     */
    public long calculateDiscountAmount(long orderAmount) {
        if (discountType == DiscountType.FIXED) {
            return discountValue;
        } else {
            // PERCENT
            return (long) (orderAmount * discountValue / 100.0);
        }
    }

    /**
     * 쿠폰 발급 (재고 차감)
     * @return 재고가 차감된 새로운 Coupon 객체
     */
    public Coupon issue() {
        CouponStock newStock = this.stock.issue();
        return Coupon.builder()
                .couponId(this.couponId)
                .name(this.name)
                .discountType(this.discountType)
                .discountValue(this.discountValue)
                .stock(newStock)
                .minOrderAmount(this.minOrderAmount)
                .expiresAt(this.expiresAt)
                .build();
    }
}
