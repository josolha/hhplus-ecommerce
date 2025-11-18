package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.domain.order.service.OrderFacade;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderFacade orderFacade;
    private final UserRepository userRepository;

    /**
     * 주문 생성 및 결제 처리
     *
     * Facade 패턴을 적용하여 복잡한 주문 생성 로직을 OrderFacade에 위임
     * UseCase는 요청/응답 변환과 트랜잭션 경계만 관리
     *
     * @param request 주문 요청
     * @return 주문 결과
     */
    @Transactional
    public OrderResponse execute(CreateOrderRequest request) {
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
