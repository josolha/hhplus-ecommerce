package com.sparta.ecommerce.application.user.service;

import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.domain.user.entity.BalanceHistory;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import com.sparta.ecommerce.domain.user.repository.BalanceHistoryRepository;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잔액 충전 트랜잭션 처리 서비스
 *
 * 동시성 제어 전략:
 * - Transaction ID 기반 멱등성 보장
 * - 원자적 UPDATE로 동시 충전 방지
 * - Unique 제약 조건으로 중복 충전 차단
 */
@Service
@RequiredArgsConstructor
public class ChargeBalanceService {

    private final UserRepository userRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    /**
     * 잔액 충전 비즈니스 로직 (트랜잭션)
     * 멱등성 보장: 동일한 transactionId로 재시도해도 중복 충전되지 않음
     */
    @Transactional
    public ChargeBalanceResponse charge(String userId, ChargeBalanceRequest request) {
        // 1. 중복 충전 체크 (transactionId 확인)
        if (balanceHistoryRepository.existsByTransactionId(request.transactionId())) {
            // 이미 처리된 거래 - 기존 결과 반환 (멱등성 보장)
            User user = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            return ChargeBalanceResponse.from(user, user.getBalance(), 0L);
        }

        // 2. 사용자 존재 확인 및 충전 전 잔액 조회
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Balance previousBalance = user.getBalance();

        // 3. 원자적 잔액 업데이트 (DB 레벨에서 동시성 보장)
        int updated = userRepository.updateBalanceByUserId(userId, request.amount());
        if (updated == 0) {
            throw new UserNotFoundException(userId);
        }

        // 4. 충전 이력 저장 (transactionId unique 제약으로 중복 방지)
        try {
            balanceHistoryRepository.save(
                    BalanceHistory.builder()
                            .userId(userId)
                            .transactionId(request.transactionId())
                            .amount(request.amount())
                            .previousBalance(previousBalance.amount())
                            .currentBalance(previousBalance.amount() + request.amount())
                            .paymentMethod("CARD")
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // transactionId 중복 - 이미 다른 트랜잭션에서 처리됨
            User updatedUser = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            return ChargeBalanceResponse.from(updatedUser, updatedUser.getBalance(), 0L);
        }

        // 5. 업데이트된 사용자 정보 조회
        User updatedUser = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 6. 응답 생성
        return ChargeBalanceResponse.from(updatedUser, previousBalance, request.amount());
    }
}
