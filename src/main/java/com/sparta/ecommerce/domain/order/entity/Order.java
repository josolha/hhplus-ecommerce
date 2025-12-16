package com.sparta.ecommerce.domain.order.entity;

import com.sparta.ecommerce.infrastructure.jpa.BaseEntity;
import com.sparta.ecommerce.domain.order.OrderStatus;
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
 * 주문 엔티티
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_status_created_at", columnList = "status, created_at"),
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_user_id_status", columnList = "user_id, status")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Order extends BaseEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "user_coupon_id")
    private String userCouponId;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "discount_amount", nullable = false)
    @Builder.Default
    private long discountAmount = 0;

    @Column(name = "final_amount", nullable = false)
    private long finalAmount;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
