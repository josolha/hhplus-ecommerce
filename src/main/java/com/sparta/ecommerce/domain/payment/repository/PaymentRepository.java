package com.sparta.ecommerce.domain.payment.repository;

import com.sparta.ecommerce.domain.payment.PaymentStatus;
import com.sparta.ecommerce.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 결제 저장소 인터페이스
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    /**
     * 주문 ID로 결제 조회
     */
    Optional<Payment> findByOrderId(String orderId);

    /**
     * 사용자 ID로 결제 목록 조회
     */
    List<Payment> findByUserId(String userId);

    /**
     * 사용자 ID와 상태로 결제 목록 조회
     */
    List<Payment> findByUserIdAndStatus(String userId, PaymentStatus status);

    /**
     * 결제 상태로 조회
     */
    List<Payment> findByStatus(PaymentStatus status);
}
