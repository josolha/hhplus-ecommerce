package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.IntegrationTestBase;
import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.domain.cart.entity.Cart;
import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.cart.repository.CartRepository;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.OrderStatus;
import com.sparta.ecommerce.domain.order.repository.OrderItemRepository;
import com.sparta.ecommerce.domain.order.repository.OrderRepository;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.vo.Stock;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateOrderUserCaseIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    private User testUser;
    private Product testProduct;
    private Cart testCart;

    @BeforeEach
    void setUpTest(){
        // 1. 사용자 생성 (잔액 충분하게)
        testUser = User.builder()
                .name("테스트유저")
                .email("test@example.com")
                .balance(new Balance(100000L))  // 10만원
                .build();
        userRepository.save(testUser);

        // 2. 상품 생성 (재고 충분하게)
        testProduct = Product.builder()
                .name("테스트상품")
                .price(10000L)
                .stock(new Stock(100))
                .build();
        productRepository.save(testProduct);

        // 3. 장바구니 생성
        testCart = Cart.builder()
                .userId(testUser.getUserId())
                .build();
        cartRepository.save(testCart);

        // 4. 장바구니에 상품 담기
        CartItem cartItem = CartItem.builder()
                 .cartId(testCart.getCartId())
                .productId(testProduct.getProductId())
                .quantity(2)
                .build();
        cartItemRepository.save(cartItem);

        // 5. 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("주문 생성 성공 -  장바구니 상품으로 주문")
    public void createOrder_Success() throws Exception{
        //given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // 초기 상태 저장
        long initialBalance = testUser.getBalance().amount();
        int initialStock = testProduct.getStock().getQuantity();

        //when
        OrderResponse response = createOrderUseCase.execute(request);
        entityManager.flush();
        entityManager.clear();

        //then - OrderResponse 검증
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(20000L);
        assertThat(response.discountAmount()).isEqualTo(0L);
        assertThat(response.finalAmount()).isEqualTo(20000L);

        // then - Order 엔티티 DB 저장 검증
        Order savedOrder = orderRepository.findById(response.orderId())
                .orElseThrow(() -> new AssertionError("Order not found"));
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(savedOrder.getUserId()).isEqualTo(testUser.getUserId());
        assertThat(savedOrder.getTotalAmount()).isEqualTo(20000L);

        // then - User 잔액 차감 검증
        User updatedUser = userRepository.findById(testUser.getUserId())
                .orElseThrow(() -> new AssertionError("User not found"));
        assertThat(updatedUser.getBalance().amount())
                .isEqualTo(initialBalance - 20000L)
                .isEqualTo(80000L);

        // then - Product 재고 감소 검증
        Product updatedProduct = productRepository.findById(testProduct.getProductId())
                .orElseThrow(() -> new AssertionError("Product not found"));
        assertThat(updatedProduct.getStock().getQuantity())
                .isEqualTo(initialStock - 2)
                .isEqualTo(98);

        // then - CartItem 삭제 검증
        List<CartItem> remainingItems = cartItemRepository.findByCartId(testCart.getCartId());
        assertThat(remainingItems).isEmpty();
    }

    @Test
    @DisplayName("주문 생성 성공 - 쿠폰 적용")
    void createOrder_Success_WithCoupon() {
        // given
        // 쿠폰 데이터 생성
        Coupon coupon = Coupon.builder()
                .name("5000원 할인쿠폰")
                .discountType(com.sparta.ecommerce.domain.coupon.DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(
                        100,  // totalQuantity - 총 수량
                        0,    // issuedQuantity - 발급된 수량
                        100   // remainingQuantity - 남은 수량 (totalQuantity - issuedQuantity)
                ))
                .minOrderAmount(10000L)  // 최소 1만원 이상 주문
                .expiresAt(LocalDateTime.now().plusDays(30))  // 만료일 (30일 후)
                .build();
        couponRepository.save(coupon);

        // 2. 사용자에게 쿠폰 발급
        UserCoupon userCoupon = UserCoupon.builder()
                .userId(testUser.getUserId())
                .couponId(coupon.getCouponId())
                .usedAt(null)  // 미사용
                .expiresAt(coupon.getExpiresAt())  // Coupon의 만료일 사용
                .build();
        userCouponRepository.save(userCoupon);


        entityManager.flush();
        entityManager.clear();

        // request에 쿠폰 ID 포함
        CreateOrderRequest request = new CreateOrderRequest(
                testUser.getUserId(),
                userCoupon.getUserCouponId()  // 사용자 쿠폰 ID
        );

        // when
        OrderResponse response = createOrderUseCase.execute(request);
        entityManager.flush();
        entityManager.clear();

        // then - OrderResponse 검증
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(20000L);
        assertThat(response.discountAmount()).isEqualTo(5000L);
        assertThat(response.finalAmount()).isEqualTo(15000L);  // 20000 - 5000

        // then - UserCoupon 사용 처리 검증
        UserCoupon usedCoupon = userCouponRepository.findById(userCoupon.getUserCouponId())
                .orElseThrow(() -> new AssertionError("UserCoupon not found"));
        assertThat(usedCoupon.getUsedAt()).isNotNull();
        assertThat(usedCoupon.isUsed()).isTrue();

        // then - User 잔액 차감 검증 (쿠폰 적용)
        User updatedUser = userRepository.findById(testUser.getUserId())
                .orElseThrow(() -> new AssertionError("User not found"));
        assertThat(updatedUser.getBalance().amount()).isEqualTo(85000L);  // 100000 - 15000

        // then - Product 재고 감소 검증
        Product updatedProduct = productRepository.findById(testProduct.getProductId())
                .orElseThrow(() -> new AssertionError("Product not found"));
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(98);  // 100 - 2

        // then - Order 저장 검증
        Order savedOrder = orderRepository.findById(response.orderId())
                .orElseThrow(() -> new AssertionError("Order not found"));
        assertThat(savedOrder.getUserCouponId()).isEqualTo(userCoupon.getUserCouponId());
        assertThat(savedOrder.getDiscountAmount()).isEqualTo(5000L);
    }


}