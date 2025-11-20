package com.sparta.ecommerce.application.order;

import com.sparta.ecommerce.IntegrationTestBase;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 *
 * [예상 결과]
 * - 이 테스트는 실패할 것으로 예상됨 (동시성 제어 미적용 상태)
 * - 재고가 음수가 되거나, 실제 차감량보다 적게 차감되는 문제 발생 예상
 */
public class CreateOrderConcurrencyTest extends IntegrationTestBase {

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

    @BeforeEach
    public void setUp() {

        // 1. 테스트용 상품 생성 (재고 100개)
        testProduct = Product.builder()
                .name("동시성테스트상품")
                .price(10000L)
                .stock(new Stock(100))  // 초기 재고 100개
                .build();
        productRepository.save(testProduct);

        // 2. 테스트용 사용자 50명 생성 (각자 충분한 잔액)
        testUsers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User user = User.builder()
                    .name("동시성테스트유저" + i)
                    .email("concurrency-test" + i + "@example.com")
                    .balance(new Balance(100000L))  // 각 사용자 10만원
                    .build();
            userRepository.save(user);
            testUsers.add(user);

            // 각 사용자의 장바구니 생성
            Cart cart = Cart.builder()
                    .userId(user.getUserId())
                    .build();
            cartRepository.save(cart);

            // 장바구니에 상품 2개씩 담기 (총 50명 * 2개 = 100개)
            CartItem cartItem = CartItem.builder()
                    .cartId(cart.getCartId())
                    .productId(testProduct.getProductId())
                    .quantity(2)
                    .build();
            cartItemRepository.save(cartItem);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[동시성 문제 재현] 50명이 동시에 주문 시 재고 차감 Lost Update 발생")
    void createOrder_ConcurrentStock_LostUpdate() throws InterruptedException {
        // given
        int threadCount = 50;  // 50명의 사용자
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

        // then - 결과 검증
        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.println("성공한 주문 수: " + successCount.get());
        System.out.println("실패한 주문 수: " + failCount.get());

        // 실제 DB에서 재고 확인
        entityManager.clear();
        Product updatedProduct = productRepository.findById(testProduct.getProductId()).get();
        int finalStock = updatedProduct.getStock().quantity();

        System.out.println("초기 재고: 100");
        System.out.println("최종 재고: " + finalStock);
        System.out.println("예상 재고: 0 (100 - 50명 * 2개)");
        System.out.println("차감된 재고: " + (100 - finalStock));

        // 동시성 문제가 없다면:
        // - 모든 주문이 성공해야 함 (successCount = 50)
        // - 재고는 0이 되어야 함 (100 - 100 = 0)

        // 동시성 문제가 발생한다면:
        // - 재고가 음수가 되거나
        // - 일부 주문이 실패하거나
        // - 재고가 예상보다 많이 남아있을 수 있음 (Lost Update)

        // 이 테스트는 동시성 문제를 드러내기 위한 것이므로
        // 실패할 것으로 예상됨
        assertThat(finalStock).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(50);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[동시성 문제 재현] 100명이 재고 10개 상품 주문 시 초과 판매 발생")
    void createOrder_ConcurrentStock_Overselling() throws InterruptedException {
        // given - 재고가 10개밖에 없는 상품 생성
        Product limitedProduct = Product.builder()
                .name("한정상품")
                .price(10000L)
                .stock(new Stock(10))  // 재고 10개만
                .build();
        productRepository.save(limitedProduct);

        // 100명의 사용자 생성
        List<User> manyUsers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User user = User.builder()
                    .name("유저" + i)
                    .email("user" + i + "@example.com")
                    .balance(new Balance(100000L))
                    .build();
            userRepository.save(user);
            manyUsers.add(user);

            // 장바구니 생성 및 상품 1개씩 담기
            Cart cart = Cart.builder()
                    .userId(user.getUserId())
                    .build();
            cartRepository.save(cart);

            CartItem cartItem = CartItem.builder()
                    .cartId(cart.getCartId())
                    .productId(limitedProduct.getProductId())
                    .quantity(1)
                    .build();
            cartItemRepository.save(cartItem);
        }

        entityManager.clear();

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
        entityManager.clear();
        Product updatedProduct = productRepository.findById(limitedProduct.getProductId()).get();
        int finalStock = updatedProduct.getStock().quantity();

        System.out.println("=== 초과 판매 테스트 결과 ===");
        System.out.println("초기 재고: 10");
        System.out.println("최종 재고: " + finalStock);
        System.out.println("성공한 주문: " + successCount.get());
        System.out.println("실패한 주문: " + failCount.get());

        // 동시성 제어가 없다면:
        // - 10개 이상 판매될 수 있음 (초과 판매)
        // - 재고가 음수가 될 수 있음

        // 올바른 동작:
        // - 성공한 주문 = 10개
        // - 실패한 주문 = 90개
        // - 최종 재고 = 0

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(90);
        assertThat(finalStock).isEqualTo(0);
    }
}
