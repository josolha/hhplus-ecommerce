package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.domain.cart.entity.Cart;
import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.cart.repository.CartRepository;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.vo.Stock;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class CreateOrderIntegrationTest {

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

    private User testUser;
    private Product testProduct;
    private Cart testCart;

    @BeforeEach
    void setUp() {
        // 테스트 유저 생성
        testUser = userRepository.save(User.builder()
                .email("test@test.com")
                .name("테스트유저")
                .balance(new Balance(1_000_000L))
                //.version(0L)
                .build());

        // 테스트 상품 생성 (재고 10개)
        testProduct = productRepository.save(Product.builder()
                .name("테스트상품")
                .description("테스트용")
                .price(10000L)
                .stock(new Stock(10))
                .category("테스트")
                .build());

        // 장바구니 생성
        testCart = cartRepository.save(Cart.builder()
                .userId(testUser.getUserId())
                .build());

        // 장바구니 아이템 추가 (수량 1개)
        cartItemRepository.save(CartItem.builder()
                .cartId(testCart.getCartId())
                .productId(testProduct.getProductId())
                .quantity(1)
                .build());
    }

    @Test
    @DisplayName("주문 생성 시 상품 재고가 차감되어야 한다")
    void createOrder_shouldDecreaseProductStock() {
        // given
        int initialStock = testProduct.getStock().getQuantity();
        System.out.println("초기 재고: " + initialStock);

        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // when
        OrderResponse response = createOrderUseCase.execute(request);

        // then
        System.out.println("주문 ID: " + response.orderId());
        System.out.println("주문 금액: " + response.finalAmount());

        // 상품 다시 조회해서 재고 확인
        Product updatedProduct = productRepository.findById(testProduct.getProductId())
                .orElseThrow();

        int finalStock = updatedProduct.getStock().getQuantity();
        System.out.println("최종 재고: " + finalStock);

        assertThat(finalStock).isEqualTo(initialStock - 1);
        assertThat(response.orderId()).isNotNull();
    }
}
