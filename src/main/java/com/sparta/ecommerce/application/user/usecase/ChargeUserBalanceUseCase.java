package com.sparta.ecommerce.application.user.usecase;

import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.application.user.service.ChargeBalanceService;
import com.sparta.ecommerce.infrastructure.aop.annotation.Trace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사용자 잔액 충전 유스케이스
 *
 * 동시성 제어 전략:
 * - Transaction ID 기반 멱등성 보장
 * - 원자적 UPDATE로 동시 충전 방지
 * - 분산 락 불필요 (DB 레벨 제약으로 충분)
 *
 * 변경 이력:
 * - 기존: @DistributedLock 사용 (동시성 제어 불완전)
 * - 변경: Transaction ID + 원자적 UPDATE (진정한 멱등성)
 */
@Service
@RequiredArgsConstructor
public class ChargeUserBalanceUseCase {

    private final ChargeBalanceService chargeBalanceService;

    @Trace
    public ChargeBalanceResponse execute(String userId, ChargeBalanceRequest request) {
        return chargeBalanceService.charge(userId, request);
    }
}
