package com.sparta.ecommerce.domain.product.vo;

import com.sparta.ecommerce.domain.product.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

/**
 * 재고 Value Object
 * 재고 수량과 관련된 비즈니스 로직을 캡슐화
 */

@Embeddable
public record Stock(
        @Column(name = "stock", nullable = false)
        int quantity
) {

    /**
     * Compact constructor - 유효성 검증
     */
    public Stock {
        if (quantity < 0) {
            throw new IllegalArgumentException("재고는 음수일 수 없습니다");
        }
    }
    /**
     * 재고 차감
     * @param amount 차감할 수량
     * @return 차감된 새로운 Stock 객체 (불변)
     * @throws InsufficientStockException 재고가 부족한 경우
     */
    public Stock decrease(int amount) {
        if (this.quantity < amount) {
            throw new InsufficientStockException(
                    String.format("재고가 부족합니다. 현재: %d, 요청: %d", this.quantity, amount)
            );
        }
        return new Stock(this.quantity - amount);
    }

    /**
     * 재고 증가
     * @param amount 증가할 수량
     * @return 증가된 새로운 Stock 객체 (불변)
     */
    public Stock increase(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("증가량은 음수일 수 없습니다");
        }
        return new Stock(this.quantity + amount);
    }

    /**
     * 요청한 수량만큼 재고가 있는지 확인
     * @param requestedAmount 요청 수량
     * @return 재고 충분 여부
     */
    public boolean isAvailable(int requestedAmount) {
        return this.quantity >= requestedAmount;
    }

    /**
     * 재고가 없는지 확인
     * @return 재고 소진 여부
     */
    public boolean isOutOfStock() {
        return this.quantity == 0;
    }
}
