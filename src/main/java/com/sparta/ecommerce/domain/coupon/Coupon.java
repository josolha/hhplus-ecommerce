package com.sparta.ecommerce.domain.coupon;

import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 엔티티
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {
    private String couponId;
    private String name;
    private DiscountType discountType;
    private int discountValue;
    private CouponStock stock;  // VO로 변경
    private int minOrderAmount;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

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
    public boolean meetsMinOrderAmount(int orderAmount) {
        return orderAmount >= minOrderAmount;
    }

    /**
     * 할인 금액 계산
     */
    public int calculateDiscountAmount(int orderAmount) {
        if (discountType == DiscountType.FIXED) {
            return discountValue;
        } else {
            // PERCENT
            return (int) (orderAmount * discountValue / 100.0);
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
                .createdAt(this.createdAt)
                .build();
    }
}
