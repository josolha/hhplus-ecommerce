package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 발급 동시성 테스트 (Redisson 분산 락)
 *
 * Testcontainers로 MySQL과 Redis를 실행하여
 * 실제 분산 락 환경에서 동시성 제어를 검증합니다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class IssueCouponConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce")
            .withUsername("root")
            .withPassword("root")
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    private Coupon limitedCoupon;
    private List<User> testUsers;
    private List<String> userIds = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        // 1. 선착순 쿠폰 생성 (재고 50개)
        limitedCoupon = Coupon.builder()
                .name("선착순 5000원 할인쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(50, 0, 50))
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        couponRepository.save(limitedCoupon);

        // 2. 테스트용 사용자 100명 생성
        testUsers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User user = User.builder()
                    .name("쿠폰테스트유저" + i)
                    .email("coupon-test" + i + "@example.com")
                    .balance(new Balance(100000L))
                    .build();
            userRepository.save(user);
            testUsers.add(user);
            userIds.add(user.getUserId());
        }
    }

    @AfterEach
    public void tearDown() {
        userCouponRepository.deleteAll();
        userIds.forEach(id -> userRepository.deleteById(id));
        couponRepository.deleteById(limitedCoupon.getCouponId());
    }

    @Test
    @DisplayName("[동시성 제어 검증] 100명이 재고 50개 쿠폰 동시 발급 시 정확히 50명만 성공")
    void issueCoupon_ConcurrentIssue_Success() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 쿠폰 발급 시도 (하지만 재고는 50개)
        for (User user : testUsers) {
            executorService.execute(() -> {
                try {
                    UserCouponResponse response = issueCouponUseCase.execute(
                            user.getUserId(),
                            limitedCoupon.getCouponId()
                    );
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
        Coupon updatedCoupon = couponRepository.findById(limitedCoupon.getCouponId()).orElseThrow();
        int issuedQuantity = updatedCoupon.getStock().issuedQuantity();
        int remainingQuantity = updatedCoupon.getStock().remainingQuantity();
        long actualIssuedCount = userCouponRepository.findByCouponId(limitedCoupon.getCouponId()).size();

        System.out.println("\n=== [Redisson 분산락] 쿠폰 동시성 제어 테스트 결과 ===");
        System.out.println("초기 재고: 50");
        System.out.println("동시 요청: 100명");
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("발급 실패: " + failCount.get());
        System.out.println("Coupon.issuedQuantity: " + issuedQuantity);
        System.out.println("Coupon.remainingQuantity: " + remainingQuantity);
        System.out.println("실제 UserCoupon 개수: " + actualIssuedCount);
        System.out.println("✅ 분산락으로 동시성 제어 성공!");

        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);
        assertThat(issuedQuantity).isEqualTo(50);
        assertThat(remainingQuantity).isEqualTo(0);
        assertThat(actualIssuedCount).isEqualTo(50);
    }

    @Test
    @DisplayName("[동시성 제어 검증] 같은 사용자가 동시에 10번 발급 시도 시 1번만 성공")
    void issueCoupon_SameUser_OnlyOneSuccess() throws InterruptedException {
        // given - 단일 사용자
        User singleUser = testUsers.get(0);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 같은 사용자가 동시에 10번 발급 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    UserCouponResponse response = issueCouponUseCase.execute(
                            singleUser.getUserId(),
                            limitedCoupon.getCouponId()
                    );
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
        long userCouponCount = userCouponRepository.findByUserId(singleUser.getUserId()).stream()
                .filter(uc -> uc.getCouponId().equals(limitedCoupon.getCouponId()))
                .count();

        System.out.println("\n=== [Redisson 분산락] 중복 발급 방지 테스트 결과 ===");
        System.out.println("동시 요청: 10회 (같은 사용자)");
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("발급 실패: " + failCount.get());
        System.out.println("실제 발급된 UserCoupon 개수: " + userCouponCount);
        System.out.println("✅ 중복 발급 방지 성공!");

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
        assertThat(userCouponCount).isEqualTo(1);
    }

    @Test
    @DisplayName("[동시성 제어 검증] 재고 1개 쿠폰에 10명 동시 발급 시 1명만 성공")
    void issueCoupon_LastOne_OnlyOneSuccess() throws InterruptedException {
        // given - 재고 1개만 남은 쿠폰
        Coupon lastOneCoupon = Coupon.builder()
                .name("마지막 1개 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(1, 0, 1))
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        couponRepository.save(lastOneCoupon);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 10명이 동시에 발급 시도 (하지만 재고 1개)
        for (int i = 0; i < threadCount; i++) {
            User user = testUsers.get(i);
            executorService.execute(() -> {
                try {
                    UserCouponResponse response = issueCouponUseCase.execute(
                            user.getUserId(),
                            lastOneCoupon.getCouponId()
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        Coupon updatedCoupon = couponRepository.findById(lastOneCoupon.getCouponId()).orElseThrow();
        long actualIssuedCount = userCouponRepository.findByCouponId(lastOneCoupon.getCouponId()).size();

        System.out.println("\n=== [Redisson 분산락] 마지막 1개 경쟁 테스트 결과 ===");
        System.out.println("초기 재고: 1");
        System.out.println("동시 요청: 10명");
        System.out.println("발급 성공: " + successCount.get());
        System.out.println("발급 실패: " + failCount.get());
        System.out.println("실제 발급 개수: " + actualIssuedCount);
        System.out.println("✅ 마지막 1개 경쟁 제어 성공!");

        // 정리
        couponRepository.deleteById(lastOneCoupon.getCouponId());

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
        assertThat(actualIssuedCount).isEqualTo(1);
    }
}
