package com.sparta.ecommerce.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="balance_history")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class BalanceHistory {

    @Id
    @Column(name="id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private long amount;

    @Column(name="previous_balance",nullable = false)
    private long previousBalance;

    @Column(name="current_balance",nullable = false)
    private long currentBalance;

    @Column(name = "payment_method") //Todo
    private String paymentMethod;

    @Column(name = "charged_at", nullable = false)
    private LocalDateTime chargedAt;

    @PrePersist
    protected void onCreate() {
        if(chargedAt == null){
            chargedAt = LocalDateTime.now();
        }
    }



}
