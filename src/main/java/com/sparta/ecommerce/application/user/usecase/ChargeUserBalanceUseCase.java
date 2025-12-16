package com.sparta.ecommerce.application.user.usecase;

import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.application.user.service.ChargeBalanceService;
import com.sparta.ecommerce.infrastructure.aop.annotation.DistributedLock;
import com.sparta.ecommerce.infrastructure.aop.annotation.Trace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사용자 잔액 충전 유스케이스
 *
 * 동시성 제어 전략:
 * - Redisson 분산 락 (Redis 기반)
 * - 락 키: "lock:user:balance:{userId}"
 * - 락 획득 대기 시간: 10초
 * - 락 자동 해제 시간: 3초
 * - 다중 서버 환경에서 안전한 동시성 제어
 */
@Service
@RequiredArgsConstructor
public class ChargeUserBalanceUseCase {

    private final ChargeBalanceService chargeBalanceService;

    @Trace
    @DistributedLock(key = "'user:balance:'.concat(#userId)")
    public ChargeBalanceResponse execute(String userId, ChargeBalanceRequest request) {
        return chargeBalanceService.charge(userId, request);
    }
}
