package com.sparta.ecommerce.application.user;

import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 잔액 충전 동시성 테스트
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class ChargeBalanceConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce")
            .withUsername("root")
            .withPassword("root")
            .withReuse(true);

    @Autowired
    private ChargeUserBalanceUseCase chargeUserBalanceUseCase;

    @Autowired
    private UserRepository userRepository;

    private String testUserId;

    @BeforeEach
    public void setUp() {
        User testUser = User.builder()
                .name("잔액테스트유저")
                .email("balance-test@example.com")
                .balance(new Balance(0L))
                .build();
        testUser = userRepository.save(testUser);
        testUserId = testUser.getUserId();
    }

    @AfterEach
    public void tearDown() {
        userRepository.deleteById(testUserId);
    }

    /**
     * 동시성 제어가 적용된 UseCase를 통한 테스트
     * - Redisson 분산 락으로 동시성 제어
     * - 모든 요청이 순차적으로 처리되어 100% 성공
     */
    @Nested
    @DisplayName("동시성 제어 검증 - UseCase 사용")
    class ConcurrencyControlTest {

        @Test
        @DisplayName("10번 동시 충전 시 분산 락으로 데이터 정합성 보장")
        void chargeBalance_Concurrent_Success() throws InterruptedException {
            // given
            long chargeAmount = 10000L;
            int threadCount = 10;

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.execute(() -> {
                    try {
                        ChargeBalanceRequest request = new ChargeBalanceRequest(chargeAmount);
                        chargeUserBalanceUseCase.execute(testUserId, request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        System.out.println("충전 실패: " + e.getClass().getSimpleName());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            User updatedUser = userRepository.findById(testUserId).orElseThrow();
            long finalBalance = updatedUser.getBalance().amount();
            long expectedBalance = chargeAmount * threadCount;

            System.out.println("\n=== 분산 락 동시성 제어 테스트 결과 ===");
            System.out.println("성공한 충전 수: " + successCount.get());
            System.out.println("실패한 충전 수: " + failCount.get());
            System.out.println("예상 잔액: " + expectedBalance + "원");
            System.out.println("실제 잔액: " + finalBalance + "원");

            // 분산 락: 모든 요청이 성공해야 함
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failCount.get()).isEqualTo(0);
            assertThat(finalBalance).isEqualTo(expectedBalance);
        }

        @Test
        @DisplayName("100번 동시 충전 시 분산 락으로 데이터 정합성 보장")
        void chargeBalance_MassiveConcurrent_Success() throws InterruptedException {
            // given
            long chargeAmount = 1000L;
            int threadCount = 100;

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.execute(() -> {
                    try {
                        ChargeBalanceRequest request = new ChargeBalanceRequest(chargeAmount);
                        chargeUserBalanceUseCase.execute(testUserId, request);
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
            User updatedUser = userRepository.findById(testUserId).orElseThrow();
            long finalBalance = updatedUser.getBalance().amount();
            long expectedBalance = chargeAmount * threadCount;

            System.out.println("\n=== 대량 분산 락 동시성 제어 테스트 결과 ===");
            System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
            System.out.println("예상 잔액: " + expectedBalance + "원");
            System.out.println("실제 잔액: " + finalBalance + "원");

            // 분산 락: 모든 요청이 성공해야 함
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failCount.get()).isEqualTo(0);
            assertThat(finalBalance).isEqualTo(expectedBalance);
        }
    }

    /*
     * 동시성 제어가 없는 직접 Repository 호출 테스트
     * - @Version이 활성화된 상태에서는 Lost Update 대신 OptimisticLockException 발생
     * - Lost Update 재현을 위해서는 @Version 주석처리 필요
     *
    @Nested
    @DisplayName("동시성 문제 재현 - 직접 Repository 사용")
    class ConcurrencyProblemTest {

        @Test
        @DisplayName("동시성 제어 없이 직접 수정 시 Lost Update 발생")
        void directUpdate_Concurrent_LostUpdate() throws InterruptedException {
            // given
            User user = userRepository.findById(testUserId).orElseThrow();
            user.chargeBalance(50000L);
            userRepository.save(user);

            int threadCount = 20;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // when - 동시성 제어 없이 직접 Repository 호출
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executorService.execute(() -> {
                    try {
                        User u = userRepository.findById(testUserId).orElseThrow();
                        if (index < 10) {
                            u.chargeBalance(10000);
                        } else {
                            u.deductBalance(10000);
                        }
                        userRepository.save(u);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        System.out.println("실패: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            User updatedUser = userRepository.findById(testUserId).orElseThrow();
            long finalBalance = updatedUser.getBalance().amount();
            long expectedBalance = 50000L;  // 50000 + (10000*10) - (10000*10) = 50000

            System.out.println("\n=== 동시성 문제 재현 테스트 결과 ===");
            System.out.println("초기 잔액: 50000원");
            System.out.println("충전: +10000원 * 10번");
            System.out.println("차감: -10000원 * 10번");
            System.out.println("예상 잔액: " + expectedBalance + "원");
            System.out.println("실제 잔액: " + finalBalance + "원");
            System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());

            if (finalBalance != expectedBalance) {
                System.out.println("⚠️ Lost Update 발생! 차이: " + (finalBalance - expectedBalance) + "원");
            }

            // 동시성 문제로 인해 예상과 다를 수 있음을 확인
            // 이 테스트는 문제를 보여주는 것이 목적
            assertThat(finalBalance)
                    .as("동시성 제어 없이는 Lost Update가 발생할 수 있음")
                    .isNotEqualTo(expectedBalance);
        }
    }
    */
}
