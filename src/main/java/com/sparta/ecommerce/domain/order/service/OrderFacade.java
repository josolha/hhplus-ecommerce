package com.sparta.ecommerce.domain.order.service;

import com.sparta.ecommerce.domain.cart.entity.Cart;
import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.cart.exception.EmptyCartException;
import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.cart.repository.CartRepository;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.order.OrderStatus;
import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.repository.OrderRepository;
import com.sparta.ecommerce.domain.payment.PaymentMethod;
import com.sparta.ecommerce.domain.payment.entity.Payment;
import com.sparta.ecommerce.domain.payment.service.PaymentService;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 Facade
 *
 * 주문 생성의 복잡한 흐름을 캡슐화하는 Facade 패턴 적용
 * - 여러 도메인 서비스를 조합
 * - 트랜잭션 경계 관리
 * - 복잡한 비즈니스 흐름 단순화
 */
@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserCouponRepository userCouponRepository;

    private final OrderItemPreparationService orderItemPreparationService;
    private final OrderDiscountCalculator orderDiscountCalculator;
    private final PaymentService paymentService;

    /**
     * 주문 생성 전체 흐름
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID (nullable)
     * @return 생성된 주문과 주문 항목
     */
    @Transactional
    public OrderResult createOrder(String userId, String couponId) {
        // 1. 장바구니 조회 및 검증
        Cart cart = getCart(userId);
        List<CartItem> cartItems = getCartItems(cart);

        // 2. 주문 항목 준비 (재고 확인, OrderItem 생성)
        OrderItemPreparationService.OrderPreparation preparation =
                orderItemPreparationService.prepare(cartItems);

        // 3. 할인 계산
        long discountAmount = orderDiscountCalculator.calculate(
                userId,
                couponId,
                preparation.totalAmount()
        );
        long finalAmount = preparation.totalAmount() - discountAmount;

        // 4. 재고 차감
        deductStock(preparation.lockedProducts(), cartItems);

        // 5. 주문 생성
        Order order = createOrderEntity(userId, couponId, preparation.totalAmount(), discountAmount, finalAmount);

        // 6. 결제 처리
        Payment payment = paymentService.processPayment(order, PaymentMethod.BALANCE);

        // 7. 쿠폰 사용 처리
        applyCoupon(userId, couponId);

        // 8. 장바구니 비우기
        cartItemRepository.deleteByCartId(cart.getCartId());

        return new OrderResult(order, preparation.orderItems());
    }

    /**
     * 장바구니 조회
     */
    private Cart getCart(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new EmptyCartException("장바구니가 존재하지 않습니다"));
    }

    /**
     * 장바구니 아이템 조회 및 검증
     */
    private List<CartItem> getCartItems(Cart cart) {
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getCartId());

        if (cartItems.isEmpty()) {
            throw new EmptyCartException("장바구니가 비어있습니다");
        }

        return cartItems;
    }

    /**
     * 재고 차감
     */
    private void deductStock(List<Product> lockedProducts, List<CartItem> cartItems) {
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem cartItem = cartItems.get(i);
            Product product = lockedProducts.get(i);

            product.reserveStock(cartItem.getQuantity());
            productRepository.save(product);
        }
    }

    /**
     * 주문 엔티티 생성
     */
    private Order createOrderEntity(String userId, String couponId, long totalAmount, long discountAmount, long finalAmount) {
        Order order = Order.builder()
                .userId(userId)
                .totalAmount(totalAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .userCouponId(couponId)
                .status(OrderStatus.PENDING)
                .build();

        return orderRepository.save(order);
    }

    /**
     * 쿠폰 사용 처리
     */
    private void applyCoupon(String userId, String couponId) {
        if (couponId != null && !couponId.isEmpty()) {
            UserCoupon userCoupon = userCouponRepository.findByUserId(userId).stream()
                    .filter(uc -> uc.getCouponId().equals(couponId))
                    .findFirst()
                    .orElseThrow();

            UserCoupon usedCoupon = userCoupon.use();
            userCouponRepository.save(usedCoupon);
        }
    }

    /**
     * 주문 생성 결과
     */
    public record OrderResult(
            Order order,
            List<com.sparta.ecommerce.domain.order.entity.OrderItem> orderItems
    ) {}
}
