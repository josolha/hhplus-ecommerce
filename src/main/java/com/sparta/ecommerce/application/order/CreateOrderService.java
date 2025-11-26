package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.domain.order.service.OrderFacade;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 주문 생성 트랜잭션 처리 서비스
 * CreateOrderUseCase에서 분산 락 획득 후 호출됨
 */
@Service
@RequiredArgsConstructor
public class CreateOrderService {

    private final OrderFacade orderFacade;
    private final UserRepository userRepository;

    /**
     * 주문 생성 비즈니스 로직 (트랜잭션)
     * 분산 락 안에서 실행되어 동시성이 보장됨
     *
     * 주의: 이 메서드는 상품별 분산 락이 필요한 작업을 수행합니다.
     * OrderFacade.createOrder()는 재고 차감을 포함하므로,
     * UseCase에서 상품 ID 기반 락을 획득해야 합니다.
     */
    public OrderResponse create(CreateOrderRequest request) {
        // 1. 주문 생성 (모든 복잡한 로직은 Facade가 처리)
        OrderFacade.OrderResult result = orderFacade.createOrder(
                request.userId(),
                request.couponId()
        );

        // 2. 사용자 정보 조회 (결제 후 잔액 확인)
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException(request.userId()));

        // 3. 응답 생성
        return OrderResponse.from(
                result.order(),
                result.orderItems(),
                user.getBalance().amount()
        );
    }
}
