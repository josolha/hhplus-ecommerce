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
import com.sparta.ecommerce.infrastructure.external.ExternalDataPlatformService;
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
 * 주문 완료 이벤트 통합 테스트
 *
 * 테스트 시나리오:
 * 1. 주문 생성 시 OrderCompletedEvent 발행
 * 2. @TransactionalEventListener(AFTER_COMMIT)로 이벤트 수신
 * 3. @Async로 비동기 실행
 * 4. ExternalDataPlatformService 호출
 * 5. 외부 전송 실패 시에도 주문은 성공
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
     * SpyBean: 실제 빈을 감싸서 호출 여부를 검증할 수 있게 함
     * Note: @SpyBean은 deprecated되었지만 아직 사용 가능
     */
    @SpyBean
    private ExternalDataPlatformService externalDataPlatformService;

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
        reset(externalDataPlatformService);
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
    @DisplayName("주문 생성 시 이벤트가 발행되고 외부 데이터 전송이 성공한다")
    void orderEvent_Success() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // when
        OrderResponse response = createOrderUseCase.execute(request);

        // then - 주문 생성 성공
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(20000L);

        // then - 비동기 이벤트 리스너가 실행될 때까지 대기 (최대 5초)
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // ExternalDataPlatformService.sendOrderData()가 호출되었는지 검증
                    verify(externalDataPlatformService, times(1))
                            .sendOrderData(any(Order.class));
                });
    }

    @Test
    @DisplayName("외부 데이터 전송 실패 시에도 주문은 성공한다")
    void orderEvent_ExternalFailure_OrderStillSuccess() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // Mock: 외부 전송이 항상 실패하도록 설정
        doThrow(new RuntimeException("외부 API 오류"))
                .when(externalDataPlatformService)
                .sendOrderData(any(Order.class));

        // when
        OrderResponse response = createOrderUseCase.execute(request);

        // then - 주문 생성은 성공
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(20000L);

        // then - 외부 전송은 시도되었음 (실패했지만)
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(externalDataPlatformService, times(1))
                            .sendOrderData(any(Order.class));
                });

        // 재고/잔액 차감은 주문 성공으로 이미 검증됨 (실패했다면 주문 자체가 실패했을 것)
    }

    @Test
    @DisplayName("이벤트 리스너는 비동기로 실행된다")
    void orderEvent_ExecutesAsynchronously() throws Exception {
        // given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // Mock: 외부 전송에 2초 지연 설정
        doAnswer(invocation -> {
            Thread.sleep(2000);
            return null;
        }).when(externalDataPlatformService).sendOrderData(any(Order.class));

        // when
        long startTime = System.currentTimeMillis();
        OrderResponse response = createOrderUseCase.execute(request);
        long endTime = System.currentTimeMillis();

        // then - 주문 API는 2초를 기다리지 않고 즉시 반환 (비동기)
        long executionTime = endTime - startTime;
        assertThat(executionTime).isLessThan(1000L); // 1초 이내

        // then - 주문은 성공
        assertThat(response.orderId()).isNotNull();

        // then - 이벤트 리스너는 백그라운드에서 실행됨
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(externalDataPlatformService, times(1))
                            .sendOrderData(any(Order.class));
                });
    }

    @Test
    @DisplayName("Order 엔티티가 이벤트에 정확히 전달된다")
    void orderEvent_OrderEntityPassedCorrectly() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(testUser.getUserId(), null);

        // when
        OrderResponse response = createOrderUseCase.execute(request);

        // then
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // ArgumentCaptor로 실제 전달된 Order 검증
                    verify(externalDataPlatformService).sendOrderData(argThat(order ->
                            order.getOrderId().equals(response.orderId()) &&
                            order.getTotalAmount() == 20000L &&
                            order.getUserId().equals(testUser.getUserId())
                    ));
                });
    }
}
