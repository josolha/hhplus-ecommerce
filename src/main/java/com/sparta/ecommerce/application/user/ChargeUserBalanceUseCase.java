package com.sparta.ecommerce.application.user;

import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.domain.user.entity.BalanceHistory;
import com.sparta.ecommerce.domain.user.entity.User;

import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import com.sparta.ecommerce.domain.user.repository.BalanceHistoryRepository;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 잔액 충전 UseCase
 */
@Service
@RequiredArgsConstructor
public class ChargeUserBalanceUseCase {

    private final UserRepository userRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    @Transactional
    public ChargeBalanceResponse execute(String userId, ChargeBalanceRequest request) {
        // 1. 사용자 조회 (비관적 락 적용)
        // SQL: SELECT * FROM users WHERE user_id = ? FOR UPDATE
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2. 충전 전 잔액 저장 (응답용)
        Balance previousBalance = user.getBalance();

        // 3. 잔액 충전 (도메인 로직)
        user.chargeBalance(request.amount());

        // 4. 충전 이력 저장
        balanceHistoryRepository.save(
                BalanceHistory.builder()
                        .userId(userId)
                        .amount(request.amount())
                        .previousBalance(previousBalance.amount())
                        .currentBalance(user.getBalance().amount())
                        .paymentMethod("CARD")  // TODO: 결제 수단을 request에서 받도록 개선
                        .build()
        );

        // 5. 변경된 사용자 정보 저장
        userRepository.save(user);


        // 6. 응답 생성
        return ChargeBalanceResponse.from(user, previousBalance, request.amount());
    }
}
