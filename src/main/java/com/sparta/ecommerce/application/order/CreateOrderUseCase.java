package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.common.aop.annotation.DistributedLock;
import com.sparta.ecommerce.common.aop.annotation.Trace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 생성 유스케이스
 *
 * 동시성 제어 전략:
 * - Redisson 분산 락 (Redis 기반)
 * - 락 키: "LOCK:order:global" (전역 주문 락)
 * - 락 획득 대기 시간: 10초
 * - 락 자동 해제 시간: 3초
 * - 다중 서버 환경에서 안전한 동시성 제어
 *
 * [전역 락을 사용하는 이유]
 * - 주문은 여러 상품의 재고를 동시에 차감합니다
 * - 각 상품에 개별 락을 걸면 데드락 위험이 있습니다
 * - 단순하고 안전한 전역 락 방식을 채택했습니다
 *
 * [대안]
 * - 상품 ID 정렬 후 순차 락 획득 (데드락 방지)
 * - 하지만 구현 복잡도가 높아 전역 락 선택
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
    @DistributedLock(key = "'order:global'")
    public OrderResponse execute(CreateOrderRequest request) {
        return createOrderService.create(request);
    }
}
