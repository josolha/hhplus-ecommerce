package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import com.sparta.ecommerce.application.order.dto.OrderResponse;
import com.sparta.ecommerce.application.order.usecase.CreateOrderUseCase;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 생성 동시성 테스트
 *
 * [목적]
 * - 동시에 여러 사용자가 같은 상품을 주문할 때 재고 차감의 동시성 문제 확인
 * - Lost Update 문제가 발생하는지 검증
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class CreateOrderConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce")
            .withUsername("root")
            .withPassword("root")
            .withReuse(true);

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

    private Product testProduct;
    private List<User> testUsers;
    private List<String> userIds = new ArrayList<>();
    private List<String> cartIds = new ArrayList<>();
    private List<String> cartItemIds = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        // 1. 테스트용 상품 생성 (재고 100개)
        testProduct = Product.builder()
                .name("동시성테스트상품")
                .price(10000L)
                .stock(new Stock(100))
                .build();
        productRepository.save(testProduct);

        // 2. 테스트용 사용자 50명 생성 (각자 충분한 잔액)
        testUsers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User user = User.builder()
                    .name("동시성테스트유저" + i)
                    .email("concurrency-test" + i + "@example.com")
                    .balance(new Balance(100000L))
                    .build();
            userRepository.save(user);
            testUsers.add(user);
            userIds.add(user.getUserId());

            // 각 사용자의 장바구니 생성
            Cart cart = Cart.builder()
                    .userId(user.getUserId())
                    .build();
            cartRepository.save(cart);
            cartIds.add(cart.getCartId());

            // 장바구니에 상품 2개씩 담기
            CartItem cartItem = CartItem.builder()
                    .cartId(cart.getCartId())
                    .productId(testProduct.getProductId())
                    .quantity(2)
                    .build();
            cartItemRepository.save(cartItem);
            cartItemIds.add(cartItem.getCartItemId());
        }
    }

    @AfterEach
    public void tearDown() {
        cartItemIds.forEach(id -> cartItemRepository.deleteById(id));
        cartIds.forEach(id -> cartRepository.deleteById(id));
        userIds.forEach(id -> userRepository.deleteById(id));
        productRepository.deleteById(testProduct.getProductId());
    }

    @Test
    @DisplayName("[동시성 제어 검증] 50명이 동시에 주문 시 재고가 정확히 차감됨")
    void createOrder_ConcurrentStock_Success() throws InterruptedException {
        // given
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 50명이 동시에 주문 생성 (각자 2개씩, 총 100개)
        for (User user : testUsers) {
            executorService.execute(() -> {
                try {
                    CreateOrderRequest request = new CreateOrderRequest(user.getUserId(), null);
                    OrderResponse response = createOrderUseCase.execute(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("주문 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        Product updatedProduct = productRepository.findById(testProduct.getProductId()).orElseThrow();
        int finalStock = updatedProduct.getStock().getQuantity();

        System.out.println("\n=== 동시성 제어 테스트 결과 ===");
        System.out.println("초기 재고: 100");
        System.out.println("최종 재고: " + finalStock);
        System.out.println("성공한 주문: " + successCount.get());
        System.out.println("실패한 주문: " + failCount.get());

        assertThat(finalStock).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(50);
    }

    @Test
    @DisplayName("[동시성 제어 검증] 100명이 재고 10개 상품 주문 시 정확히 10명만 성공")
    void createOrder_ConcurrentStock_LimitedSuccess() throws InterruptedException {
        // given - 재고가 10개밖에 없는 상품 생성
        Product limitedProduct = Product.builder()
                .name("한정상품")
                .price(10000L)
                .stock(new Stock(10))
                .build();
        productRepository.save(limitedProduct);

        // 100명의 사용자 생성
        List<User> manyUsers = new ArrayList<>();
        List<String> manyUserIds = new ArrayList<>();
        List<String> manyCartIds = new ArrayList<>();
        List<String> manyCartItemIds = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            User user = User.builder()
                    .name("유저" + i)
                    .email("user" + i + "@example.com")
                    .balance(new Balance(100000L))
                    .build();
            userRepository.save(user);
            manyUsers.add(user);
            manyUserIds.add(user.getUserId());

            Cart cart = Cart.builder()
                    .userId(user.getUserId())
                    .build();
            cartRepository.save(cart);
            manyCartIds.add(cart.getCartId());

            CartItem cartItem = CartItem.builder()
                    .cartId(cart.getCartId())
                    .productId(limitedProduct.getProductId())
                    .quantity(1)
                    .build();
            cartItemRepository.save(cartItem);
            manyCartItemIds.add(cartItem.getCartItemId());
        }

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 주문 (하지만 재고는 10개)
        for (User user : manyUsers) {
            executorService.execute(() -> {
                try {
                    CreateOrderRequest request = new CreateOrderRequest(user.getUserId(), null);
                    OrderResponse response = createOrderUseCase.execute(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        Product updatedProduct = productRepository.findById(limitedProduct.getProductId()).orElseThrow();
        int finalStock = updatedProduct.getStock().getQuantity();

        System.out.println("\n=== 한정 재고 테스트 결과 ===");
        System.out.println("초기 재고: 10");
        System.out.println("최종 재고: " + finalStock);
        System.out.println("성공한 주문: " + successCount.get());
        System.out.println("실패한 주문: " + failCount.get());

        // 정리
        manyCartItemIds.forEach(id -> cartItemRepository.deleteById(id));
        manyCartIds.forEach(id -> cartRepository.deleteById(id));
        manyUserIds.forEach(id -> userRepository.deleteById(id));
        productRepository.deleteById(limitedProduct.getProductId());

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(90);
        assertThat(finalStock).isEqualTo(0);
    }
}