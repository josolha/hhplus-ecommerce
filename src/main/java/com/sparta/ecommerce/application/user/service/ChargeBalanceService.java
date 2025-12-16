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

/**
 * 잔액 충전 트랜잭션 처리 서비스
 * ChargeUserBalanceUseCase에서 분산 락 획득 후 호출됨
 */
@Service
@RequiredArgsConstructor
public class ChargeBalanceService {

    private final UserRepository userRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    /**
     * 잔액 충전 비즈니스 로직 (트랜잭션)
     * 분산 락 안에서 실행되어 동시성이 보장됨
     */
    public ChargeBalanceResponse charge(String userId, ChargeBalanceRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findByUserId(userId)
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
                        .paymentMethod("CARD")
                        .build()
        );

        // 5. 변경된 사용자 정보 저장
        userRepository.save(user);

        // 6. 응답 생성
        return ChargeBalanceResponse.from(user, previousBalance, request.amount());
    }
}
