package com.sparta.ecommerce.domain.payment.service;

import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.payment.PaymentMethod;
import com.sparta.ecommerce.domain.payment.PaymentStatus;
import com.sparta.ecommerce.domain.payment.entity.Payment;
import com.sparta.ecommerce.domain.payment.exception.PaymentFailedException;
import com.sparta.ecommerce.domain.payment.repository.PaymentRepository;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.exception.InsufficientBalanceException;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 도메인 서비스
 *
 * 결제 관련 비즈니스 로직을 처리하는 도메인 서비스
 * - 여러 엔티티(User, Order, Payment)를 조합하는 로직
 * - 결제 수단별 처리 로직 분리
 * - 결제 이력 관리
 *
 * [도메인 서비스를 사용하는 이유]
 * - User 엔티티에 결제 로직을 넣기에는 복잡함
 * - Order 엔티티에 넣기에도 부적절
 * - 여러 엔티티를 조합하는 로직이므로 도메인 서비스로 분리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    /**
     * 결제 처리
     *
     * @param order 주문 정보
     * @param method 결제 수단
     * @return 결제 결과
     */
    @Transactional
    public Payment processPayment(Order order, PaymentMethod method) {
        log.info("결제 처리 시작 - 주문ID: {}, 금액: {}, 결제수단: {}",
                order.getOrderId(), order.getFinalAmount(), method);

        // 결제 수단별 처리
        Payment payment = switch (method) {
            case BALANCE -> processBalancePayment(order);
            case CARD -> processCardPayment(order);
            case KAKAO_PAY -> processKakaoPayPayment(order);
            case TOSS_PAY -> processTossPayPayment(order);
        };

        log.info("결제 처리 완료 - 결제ID: {}, 상태: {}", payment.getPaymentId(), payment.getStatus());
        return payment;
    }

    /**
     * 잔액 결제 처리
     *
     * @param order 주문 정보
     * @return 결제 결과
     */
    private Payment processBalancePayment(Order order) {
        // 1. 결제 기록 생성 (PENDING 상태)
        Payment payment = Payment.createBalancePayment(
                order.getOrderId(),
                order.getUserId(),
                order.getFinalAmount()
        );
        paymentRepository.save(payment);

        try {
            // 2. 사용자 조회 (비관적 락)
            User user = userRepository.findByIdWithLock(order.getUserId())
                    .orElseThrow(() -> new PaymentFailedException("사용자를 찾을 수 없습니다"));

            // 3. 잔액 확인
            if (!user.getBalance().isSufficient(order.getFinalAmount())) {
                String reason = String.format("잔액 부족 (필요: %d, 현재: %d)",
                        order.getFinalAmount(), user.getBalance().amount());
                Payment failedPayment = payment.markAsFailed(reason);
                paymentRepository.save(failedPayment);
                throw new InsufficientBalanceException(reason);
            }

            // 4. 잔액 차감
            user.deductBalance(order.getFinalAmount());
            userRepository.save(user);

            // 5. 결제 성공 처리
            Payment completedPayment = payment.markAsCompleted();
            return paymentRepository.save(completedPayment);

        } catch (InsufficientBalanceException e) {
            throw e;  // 그대로 전파
        } catch (Exception e) {
            // 6. 예외 발생 시 실패 처리
            Payment failedPayment = payment.markAsFailed(e.getMessage());
            paymentRepository.save(failedPayment);
            throw new PaymentFailedException("잔액 결제 처리 중 오류 발생", e);
        }
    }

    /**
     * 카드 결제 처리 (추후 PG 연동)
     *
     * @param order 주문 정보
     * @return 결제 결과
     */
    private Payment processCardPayment(Order order) {
        // TODO: 토스페이먼트, 이니시스 등 PG사 연동
        Payment payment = Payment.createCardPayment(
                order.getOrderId(),
                order.getUserId(),
                order.getFinalAmount()
        );
        paymentRepository.save(payment);

        // 임시: 카드 결제는 구현 예정
        Payment failedPayment = payment.markAsFailed("카드 결제는 아직 구현되지 않았습니다");
        return paymentRepository.save(failedPayment);
    }

    /**
     * 카카오페이 결제 처리 (추후 구현)
     *
     * @param order 주문 정보
     * @return 결제 결과
     */
    private Payment processKakaoPayPayment(Order order) {
        // TODO: 카카오페이 API 연동
        Payment payment = Payment.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .amount(order.getFinalAmount())
                .method(PaymentMethod.KAKAO_PAY)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        Payment failedPayment = payment.markAsFailed("카카오페이 결제는 아직 구현되지 않았습니다");
        return paymentRepository.save(failedPayment);
    }

    /**
     * 토스페이 결제 처리 (추후 구현)
     *
     * @param order 주문 정보
     * @return 결제 결과
     */
    private Payment processTossPayPayment(Order order) {
        // TODO: 토스페이 API 연동
        Payment payment = Payment.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .amount(order.getFinalAmount())
                .method(PaymentMethod.TOSS_PAY)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        Payment failedPayment = payment.markAsFailed("토스페이 결제는 아직 구현되지 않았습니다");
        return paymentRepository.save(failedPayment);
    }

    /**
     * 결제 취소
     *
     * @param paymentId 결제 ID
     * @return 취소된 결제 정보
     */
    @Transactional
    public Payment cancelPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentFailedException("결제를 찾을 수 없습니다: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentFailedException("완료된 결제만 취소할 수 있습니다");
        }

        // 잔액 결제인 경우 환불 처리
        if (payment.getMethod() == PaymentMethod.BALANCE) {
            User user = userRepository.findByIdWithLock(payment.getUserId())
                    .orElseThrow(() -> new PaymentFailedException("사용자를 찾을 수 없습니다"));

            // 잔액 복구
            user.chargeBalance(payment.getAmount());
            userRepository.save(user);
        }

        // 결제 취소 처리
        Payment cancelledPayment = payment.cancel();
        return paymentRepository.save(cancelledPayment);
    }
}
