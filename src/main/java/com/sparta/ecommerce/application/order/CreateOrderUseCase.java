package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.domain.cart.entity.Cart;
import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.cart.repository.CartRepository;
import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.cart.exception.EmptyCartException;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.exception.CouponAlreadyUsedException;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.entity.OrderItem;
import com.sparta.ecommerce.domain.order.repository.OrderRepository;
import com.sparta.ecommerce.domain.order.OrderStatus;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.exception.InsufficientStockException;
import com.sparta.ecommerce.domain.product.exception.ProductNotFoundException;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.exception.InsufficientBalanceException;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;          // 장바구니 조회
    private final CartItemRepository cartItemRepository;  // 장바구니 아이템 조회
    private final ProductRepository productRepository;    // 상품 정보, 재고 차감
    private final UserRepository userRepository;          // 잔액 조회, 차감
    private final CouponRepository couponRepository;      // 쿠폰 조회
    private final UserCouponRepository userCouponRepository; // 쿠폰 사용 처리


    /**
     * 주문 생성 및 결제 처리
     * @param request 주문 요청
     * @return 주문 결과
     */
    public OrderResponse execute(CreateOrderRequest request) {
        String userId = request.userId();
        String couponId = request.couponId();

        // 1. 장바구니 조회
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new EmptyCartException("장바구니가 존재하지 않습니다"));

        // 장바구니 아이템 조회
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getCartId());

        if (cartItems.isEmpty()) {
            throw new EmptyCartException("장바구니가 비어있습니다");
        }

        // 2. 각 상품 재고 확인 + OrderItem 생성 + 총 금액 계산
        long totalAmount = 0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            // 상품 조회
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(cartItem.getProductId()));

            // 재고 확인
            if (!product.canAddToCart(cartItem.getQuantity())) {
                throw new InsufficientStockException(
                        "상품 재고가 부족합니다: " + product.getName() +
                        " (요청: " + cartItem.getQuantity() + ", 재고: " + product.getStock().quantity() + ")"
                );
            }

            // OrderItem 생성
            long price = (long) product.getPrice();
            long subtotal = price * cartItem.getQuantity();

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .unitPrice(price)
                    .quantity(cartItem.getQuantity())
                    .subtotal(subtotal)
                    .build();

            orderItems.add(orderItem);
            totalAmount += subtotal;
        }

        // 3. 쿠폰 할인 계산
        long discountAmount = 0;
        if (couponId != null && !couponId.isEmpty()) {
            // 쿠폰 조회
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰입니다"));

            // UserCoupon 조회
            UserCoupon userCoupon = userCouponRepository.findByUserId(userId).stream()
                    .filter(uc -> uc.getCouponId().equals(couponId))
                    .findFirst()
                    .orElseThrow(() -> new InvalidCouponException("발급받지 않은 쿠폰입니다"));

            // 쿠폰 유효성 검증
            if (userCoupon.isUsed()) {
                throw new CouponAlreadyUsedException(couponId);
            }
            if (userCoupon.isExpired()) {
                throw new CouponExpiredException(couponId);
            }

            // 할인 금액 계산
            discountAmount = coupon.calculateDiscountAmount((int) totalAmount);
        }

        long finalAmount = totalAmount - discountAmount;

        // 4. 잔액 확인 및 차감
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.getBalance().isSufficient((int) finalAmount)) {
            throw new InsufficientBalanceException(
                    "잔액이 부족합니다 (필요: " + finalAmount + ", 현재: " + user.getBalance().amount() + ")"
            );
        }

        user.deductBalance((int) finalAmount);
        userRepository.save(user);

        // 5. 재고 차감
        for (CartItem cartItem : cartItems) {
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(cartItem.getProductId()));

            product.reserveStock(cartItem.getQuantity());
            productRepository.save(product);
        }

        // 6. 주문 생성
        String orderId = "ORD" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        Order order = Order.builder()
                .orderId(orderId)
                .userId(userId)
                .totalAmount(totalAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .userCouponId(couponId)
                .status(OrderStatus.COMPLETED)
                .build();

        orderRepository.save(order);

        // 7. 쿠폰 사용 처리
        if (couponId != null && !couponId.isEmpty()) {
            UserCoupon userCoupon = userCouponRepository.findByUserId(userId).stream()
                    .filter(uc -> uc.getCouponId().equals(couponId))
                    .findFirst()
                    .orElseThrow();

            UserCoupon usedCoupon = userCoupon.use();
            userCouponRepository.save(usedCoupon);
        }

        // 8. 장바구니 비우기
        cartItemRepository.deleteByCartId(cart.getCartId());

        // 9. 응답 생성
        return OrderResponse.from(order, orderItems, user.getBalance().amount());
    }
}
