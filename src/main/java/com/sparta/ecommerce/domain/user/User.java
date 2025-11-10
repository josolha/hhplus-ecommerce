package com.sparta.ecommerce.domain.user;

import com.sparta.ecommerce.domain.user.vo.Balance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 도메인 엔티티
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String userId;
    private String name;
    private Balance balance;

    /**
     * 잔액 충전
     * @param amount 충전할 금액
     */
    public void chargeBalance(long amount) {
        this.balance = this.balance.charge(amount);
    }

    /**
     * 잔액 차감
     * @param amount 차감할 금액
     */
    public void deductBalance(long amount) {
        this.balance = this.balance.deduct(amount);
    }
}
