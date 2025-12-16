package com.sparta.ecommerce.domain.user.entity;

import com.sparta.ecommerce.infrastructure.jpa.BaseEntity;
import com.sparta.ecommerce.domain.user.vo.Balance;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 도메인 엔티티
 */
@Entity
@Table(name="users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @Column(name="id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Embedded
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
