package com.sparta.ecommerce.domain.user.vo;

import com.sparta.ecommerce.domain.user.exception.InsufficientBalanceException;

/**
 * 잔액 Value Object
 * 잔액과 관련된 비즈니스 로직을 캡슐화
 */
public record Balance(long amount) {

    /**
     * Compact constructor - 유효성 검증
     */
    public Balance {
        if (amount < 0) {
            throw new IllegalArgumentException("잔액은 음수일 수 없습니다");
        }
    }

    /**
     * 초기 잔액 (0원)
     */
    public static Balance zero() {
        return new Balance(0);
    }

    /**
     * 잔액 충전
     * @param chargeAmount 충전할 금액
     * @return 충전된 새로운 Balance 객체 (불변)
     */
    public Balance charge(long chargeAmount) {
        if (chargeAmount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다");
        }
        return new Balance(this.amount + chargeAmount);
    }

    /**
     * 잔액 차감
     * @param deductAmount 차감할 금액
     * @return 차감된 새로운 Balance 객체 (불변)
     * @throws InsufficientBalanceException 잔액이 부족한 경우
     */
    public Balance deduct(long deductAmount) {
        if (this.amount < deductAmount) {
            throw new InsufficientBalanceException(
                String.format("잔액이 부족합니다. 현재: %d, 요청: %d", this.amount, deductAmount)
            );
        }
        return new Balance(this.amount - deductAmount);
    }

    /**
     * 결제 가능 여부 확인
     * @param requiredAmount 필요한 금액
     * @return 잔액 충분 여부
     */
    public boolean isSufficient(long requiredAmount) {
        return this.amount >= requiredAmount;
    }
}
