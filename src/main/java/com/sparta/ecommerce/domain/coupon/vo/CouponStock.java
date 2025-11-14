package com.sparta.ecommerce.domain.coupon.vo;

import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 쿠폰 재고 Value Object
 * 쿠폰의 총 수량, 발급된 수량, 남은 수량을 관리하고, 발급 관련 비즈니스 로직을 캡슐화
 */
@Embeddable
public record CouponStock(
        @Column(name = "total_quantity", nullable = false)
        int totalQuantity,

        @Column(name = "issued_quantity", nullable = false)
        int issuedQuantity,

        @Column(name = "remaining_quantity", nullable = false)
        int remainingQuantity
) {

    /**
     * Compact constructor - 유효성 검증
     */
    public CouponStock {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("총 수량은 음수일 수 없습니다");
        }
        if (issuedQuantity < 0) {
            throw new IllegalArgumentException("발급된 수량은 음수일 수 없습니다");
        }
        if (remainingQuantity < 0) {
            throw new IllegalArgumentException("남은 수량은 음수일 수 없습니다");
        }
        if (issuedQuantity + remainingQuantity != totalQuantity) {
            throw new IllegalArgumentException("발급된 수량 + 남은 수량 = 총 수량이어야 합니다");
        }
    }

    /**
     * 쿠폰 발급 (재고 차감)
     * @return 발급된 새로운 CouponStock 객체 (불변)
     * @throws CouponSoldOutException 재고가 없는 경우
     */
    public CouponStock issue() {
        if (isOutOfStock()) {
            throw new CouponSoldOutException("쿠폰이 모두 소진되었습니다");
        }
        return new CouponStock(
                this.totalQuantity,
                this.issuedQuantity + 1,
                this.remainingQuantity - 1
        );
    }

    /**
     * 재고가 있는지 확인
     * @return 재고 존재 여부
     */
    public boolean hasStock() {
        return remainingQuantity > 0;
    }

    /**
     * 재고가 모두 소진되었는지 확인
     * @return 재고 소진 여부
     */
    public boolean isOutOfStock() {
        return remainingQuantity == 0;
    }

    /**
     * 발급률 계산 (백분율)
     * @return 발급률 (0.0 ~ 100.0)
     */
    public double getIssuanceRate() {
        if (totalQuantity == 0) {
            return 0.0;
        }
        return (double) issuedQuantity / totalQuantity * 100.0;
    }
}
