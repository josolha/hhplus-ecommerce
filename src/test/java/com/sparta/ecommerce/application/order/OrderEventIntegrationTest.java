package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.application.order.usecase.CreateOrderUseCase;
import com.sparta.ecommerce.domain.cart.entity.Cart;
import com.sparta.ecommerce.domain.cart.entity.CartItem;
import com.sparta.ecommerce.domain.cart.repository.CartItemRepository;
import com.sparta.ecommerce.domain.cart.repository.CartRepository;
import com.sparta.ecommerce.domain.order.entity.Order;
import com.sparta.ecommerce.domain.order.repository.OrderRepository;
import com.sparta.ecommerce.domain.product.entity.Product;
import com.sparta.ecommerce.domain.product.repository.ProductRepository;
import com.sparta.ecommerce.domain.product.vo.Stock;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import com.sparta.ecommerce.infrastructure.kafka.order.producer.OrderKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import jakarta.persistence.EntityManager;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 주문 완료 이벤트 통합 테스트 (Kafka 기반)
 *
 * 테스트 시나리오:
 * 1. 주문 생성 시 OrderCompletedEvent 발행
 * 2. @TransactionalEventListener(AFTER_COMMIT)로 이벤트 수신
 * 3. OrderKafkaProducer로 Kafka 메시지 발행
 * 4. Kafka Consumer가 메시지 수신 (별도 검증 필요)
 *
 * Note: @Transactional을 제거하여 AopForTransaction의 REQUIRES_NEW와 충돌 방지
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class OrderEventIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce")
            .withUsername("root")
            .withPassword("root")
            .withReuse(true);

    @Autowired
    protected EntityManager entityManager;

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

    /**
     * SpyBean: Kafka Producer 호출 여부 검증
     */
    @SpyBean
    private OrderKafkaProducer orderKafkaProducer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User testUser;
    private Product testProduct;
    private Cart testCart;

    @BeforeEach
    void setUpTest() {
        // 테스트 데이터를 별도 트랜잭션에서 커밋
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.execute(status -> {
            setupTestData();
            return null;
        });

        // Mock 초기화 (이전 테스트의 호출 기록 제거)
        reset(orderKafkaProducer);
    }

    private void setupTestData() {
        // 1. 사용자 생성
        testUser = User.builder()
                .name("테스트유저")
                .email("event-test@example.com")
                .balance(new Balance(100000L))
                .build();
        userRepository.save(testUser);

        // 2. 상품 생성
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
    }

    @Test
    @DisplayName("주문 생성 시 Kafka 메시지가 발행된다")
    void orderEvent_KafkaPublish_Success() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // when
        OrderResponse response = createOrderUseCase.execute(request);

        // then - 주문 생성 성공
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(20000L);

        // then - Kafka Producer가 메시지를 발행했는지 검증
        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(orderKafkaProducer, times(1))
                            .publishOrderCompleted(any(Order.class));
                });
    }

    @Test
    @DisplayName("Kafka 발행 실패 시에도 주문은 성공한다")
    void orderEvent_KafkaFailure_OrderStillSuccess() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // Mock: Kafka 발행이 항상 실패하도록 설정
        doThrow(new RuntimeException("Kafka 브로커 오류"))
                .when(orderKafkaProducer)
                .publishOrderCompleted(any(Order.class));

        // when
        OrderResponse response = createOrderUseCase.execute(request);

        // then - 주문 생성은 성공 (Kafka 실패와 무관)
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(20000L);

        // then - Kafka 발행은 시도되었음 (실패했지만)
        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(orderKafkaProducer, times(1))
                            .publishOrderCompleted(any(Order.class));
                });
    }

    @Test
    @DisplayName("Kafka 발행은 즉시 반환된다")
    void orderEvent_KafkaPublish_Immediate() throws Exception {
        // given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // when
        long startTime = System.currentTimeMillis();
        OrderResponse response = createOrderUseCase.execute(request);
        long endTime = System.currentTimeMillis();

        // then - 주문 API는 Kafka 발행을 기다리지 않고 즉시 반환
        long executionTime = endTime - startTime;
        assertThat(executionTime).isLessThan(2000L); // 2초 이내

        // then - 주문은 성공
        assertThat(response.orderId()).isNotNull();

        // then - Kafka 발행은 트랜잭션 커밋 후 실행됨
        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(orderKafkaProducer, times(1))
                            .publishOrderCompleted(any(Order.class));
                });
    }

    @Test
    @DisplayName("Order 엔티티가 Kafka Producer에 정확히 전달된다")
    void orderEvent_OrderEntityPassedCorrectly() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // when
        OrderResponse response = createOrderUseCase.execute(request);

        // then
        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // ArgumentCaptor로 실제 전달된 Order 검증
                    verify(orderKafkaProducer).publishOrderCompleted(argThat(order ->
                            order.getOrderId().equals(response.orderId()) &&
                            order.getTotalAmount() == 20000L &&
                            order.getUserId().equals(testUser.getUserId())
                    ));
                });
    }
}
