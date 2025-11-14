package com.sparta.ecommerce.application.user;

import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.domain.user.User;
import com.sparta.ecommerce.domain.user.UserRepository;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import com.sparta.ecommerce.domain.user.vo.Balance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사용자 잔액 충전 UseCase
 */
@Service
@RequiredArgsConstructor
public class ChargeUserBalanceUseCase {

    private final UserRepository userRepository;

    public ChargeBalanceResponse execute(String userId, ChargeBalanceRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2. 충전 전 잔액 저장 (응답용)
        Balance previousBalance = user.getBalance();

        // 3. 잔액 충전 (도메인 로직)
        user.chargeBalance(request.amount());

        // 4. 변경된 사용자 정보 저장
        userRepository.save(user);

        // 5. 응답 생성
        return ChargeBalanceResponse.from(user, previousBalance, request.amount());
    }
}
