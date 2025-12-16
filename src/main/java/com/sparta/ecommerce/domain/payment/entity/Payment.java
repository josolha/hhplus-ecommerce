package com.sparta.ecommerce.domain.payment.entity;

import com.sparta.ecommerce.infrastructure.jpa.BaseEntity;
import com.sparta.ecommerce.domain.payment.PaymentMethod;
import com.sparta.ecommerce.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 엔티티
 *
 * 결제 이력을 관리하는 엔티티
 * - 주문당 하나의 결제 기록
 * - 결제 수단, 금액, 상태 등을 기록
 * - 환불, 취소 등의 이력도 관리
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order_id", columnList = "order_id"),
        @Index(name = "idx_payments_user_id", columnList = "user_id"),
        @Index(name = "idx_payments_status", columnList = "status")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Payment extends BaseEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String paymentId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "method", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "pg_transaction_id")
    private String pgTransactionId;  // PG사 거래 ID (외부 결제 시)

    @Column(name = "failure_reason")
    private String failureReason;  // 실패 사유

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * 결제 성공 처리
     */
    public Payment markAsCompleted() {
        return Payment.builder()
                .paymentId(this.paymentId)
                .orderId(this.orderId)
                .userId(this.userId)
                .amount(this.amount)
                .method(this.method)
                .status(PaymentStatus.COMPLETED)
                .pgTransactionId(this.pgTransactionId)
                .paidAt(LocalDateTime.now())
                .build();
    }

    /**
     * 결제 실패 처리
     */
    public Payment markAsFailed(String reason) {
        return Payment.builder()
                .paymentId(this.paymentId)
                .orderId(this.orderId)
                .userId(this.userId)
                .amount(this.amount)
                .method(this.method)
                .status(PaymentStatus.FAILED)
                .failureReason(reason)
                .build();
    }

    /**
     * 결제 취소 처리
     */
    public Payment cancel() {
        return Payment.builder()
                .paymentId(this.paymentId)
                .orderId(this.orderId)
                .userId(this.userId)
                .amount(this.amount)
                .method(this.method)
                .status(PaymentStatus.CANCELLED)
                .pgTransactionId(this.pgTransactionId)
                .paidAt(this.paidAt)
                .cancelledAt(LocalDateTime.now())
                .build();
    }

    /**
     * 잔액 결제 생성 (팩토리 메서드)
     */
    public static Payment createBalancePayment(String orderId, String userId, long amount) {
        return Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .method(PaymentMethod.BALANCE)
                .status(PaymentStatus.PENDING)
                .build();
    }

    /**
     * 카드 결제 생성 (팩토리 메서드)
     */
    public static Payment createCardPayment(String orderId, String userId, long amount) {
        return Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.PENDING)
                .build();
    }
}
