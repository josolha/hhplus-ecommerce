package com.sparta.ecommerce.application.order.usecase;

import com.sparta.ecommerce.application.order.service.CreateOrderService;
import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.infrastructure.aop.annotation.DistributedLock;
import com.sparta.ecommerce.infrastructure.aop.annotation.Trace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 생성 유스케이스
 *
 * 동시성 제어 전략:
 * - Redisson 분산 락 (Redis 기반)
 * - 락 키: "lock:order:user:{userId}" (사용자별 주문 락)
 * - 락 획득 대기 시간: 5초
 * - 락 자동 해제 시간: 3초
 * - 다중 서버 환경에서 안전한 동시성 제어
 *
 * [사용자별 락을 사용하는 이유]
 * - 같은 사용자의 중복 주문 방지 (동일 사용자 동시 주문 차단)
 * - 다른 사용자는 독립적으로 주문 가능 (TPS 향상)
 * - 서로 다른 락이므로 데드락 불가능
 *
 * [재고 안전성 보장]
 * - DB 레벨 재고 검증 (UPDATE ... WHERE stock >= amount)
 * - 재고 부족 시 UPDATE 실패로 안전하게 처리
 * - 분산 락 + DB 검증 2단계 안전장치
 *
 * 주문 생성은 다음을 포함합니다:
 * - 재고 차감 (여러 상품)
 * - 결제 처리 (잔액 차감)
 * - 쿠폰 사용 처리
 */
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final CreateOrderService createOrderService;

    @Trace
    @DistributedLock(key = "'order:user:' + #request.userId")
    public OrderResponse execute(CreateOrderRequest request) {
        return createOrderService.create(request);
    }
}
