package com.sparta.ecommerce.application.user;

import com.sparta.ecommerce.IntegrationTestBase;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 잔액 충전 동시성 테스트
 *
 * [목적]
 * - 같은 사용자의 잔액을 동시에 여러 번 충전할 때 Lost Update 문제 확인
 * - READ → COMPUTE → WRITE 패턴에서 발생하는 동시성 문제 재현
 *
 * [예상 결과]
 * - 이 테스트는 실패할 것으로 예상됨 (동시성 제어 미적용 상태)
 * - 10번 충전했는데 실제로는 일부만 반영되는 Lost Update 발생 예상
 */
public class ChargeBalanceConcurrencyTest extends IntegrationTestBase {

    @Autowired
    private ChargeUserBalanceUseCase chargeUserBalanceUseCase;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    public void setUp() {
        // 테스트용 사용자 생성 (초기 잔액 0원)
        testUser = User.builder()
                .name("잔액테스트유저")
                .email("balance-test@example.com")
                .balance(new Balance(0L))  // 초기 잔액 0원
                .build();
        testUser = userRepository.save(testUser);
        // setUp의 @Transactional은 별도 트랜잭션이므로 자동 커밋됨
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // 테스트 메서드는 트랜잭션 비활성화
    @DisplayName("[동시성 문제 재현] 10번 동시 충전 시 Lost Update 발생")
    void chargeBalance_Concurrent_LostUpdate() throws InterruptedException {
        // given
        long chargeAmount = 10000L;  // 1만원씩 충전
        int threadCount = 10;  // 10번 동시 충전

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 10개 스레드가 동시에 1만원씩 충전
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    ChargeBalanceRequest request = new ChargeBalanceRequest(chargeAmount);
                    ChargeBalanceResponse response = chargeUserBalanceUseCase.execute(
                            testUser.getUserId(),
                            request
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("충전 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        entityManager.clear();
        User updatedUser = userRepository.findById(testUser.getUserId()).get();
        long finalBalance = updatedUser.getBalance().amount();

        long expectedBalance = chargeAmount * threadCount;  // 10만원 (1만원 * 10번)

        String result = String.format("\n=== 잔액 충전 동시성 테스트 결과 ===\n" +
                "초기 잔액: 0원\n" +
                "충전 금액: %d원\n" +
                "충전 횟수: %d번\n" +
                "예상 잔액: %d원\n" +
                "실제 잔액: %d원\n" +
                "성공 횟수: %d\n" +
                "실패 횟수: %d\n" +
                "손실된 금액: %d원 (손실률: %.1f%%)",
                chargeAmount, threadCount, expectedBalance, finalBalance,
                successCount.get(), failCount.get(),
                expectedBalance - finalBalance,
                (1 - (double)finalBalance / expectedBalance) * 100);

        System.out.println(result);

        // 동시성 제어가 없다면:
        // - Lost Update 발생으로 일부 충전이 누락됨
        // - finalBalance < expectedBalance

        // 올바른 동작:
        // - finalBalance = 100000 (10000 * 10)
        // - successCount = 10
        // - failCount = 0

        assertThat(successCount.get()).as(result).isEqualTo(10);
        assertThat(failCount.get()).as(result).isEqualTo(0);
        assertThat(finalBalance).as(result).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("[동시성 문제 재현] 100번 동시 충전 시 대량 Lost Update")
    void chargeBalance_MassiveConcurrent_LostUpdate() throws InterruptedException {
        // given
        long chargeAmount = 1000L;  // 1천원씩 충전
        int threadCount = 100;  // 100번 동시 충전

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 100개 스레드가 동시에 1천원씩 충전
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    ChargeBalanceRequest request = new ChargeBalanceRequest(chargeAmount);
                    chargeUserBalanceUseCase.execute(testUser.getUserId(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("충전 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        entityManager.clear();
        User updatedUser = userRepository.findById(testUser.getUserId()).get();
        long finalBalance = updatedUser.getBalance().amount();

        long expectedBalance = chargeAmount * threadCount;  // 10만원 (1천원 * 100번)

        System.out.println("=== 대량 충전 동시성 테스트 결과 ===");
        System.out.println("충전 횟수: " + threadCount + "번");
        System.out.println("예상 잔액: " + expectedBalance + "원");
        System.out.println("실제 잔액: " + finalBalance + "원");
        System.out.println("손실률: " + String.format("%.2f%%", (1 - (double) finalBalance / expectedBalance) * 100));

        // 동시성이 높을수록 Lost Update 발생 확률 증가
        assertThat(finalBalance).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("[동시성 문제 재현] 충전과 차감 동시 실행 시 잔액 불일치")
    void chargeAndDeduct_Concurrent_Inconsistency() throws InterruptedException {
        // given
        // 초기 잔액 50000원으로 설정
        testUser = userRepository.findById(testUser.getUserId()).get();
        testUser = User.builder()
                .userId(testUser.getUserId())
                .name(testUser.getName())
                .email(testUser.getEmail())
                .balance(new Balance(50000L))
                .build();
        userRepository.save(testUser);
        entityManager.flush();
        entityManager.clear();

        int threadCount = 20;  // 총 20개 스레드
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when - 10번은 충전(+10000), 10번은 차감(-10000) 동시 실행
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.execute(() -> {
                try {
                    if (index < 10) {
                        // 충전
                        ChargeBalanceRequest request = new ChargeBalanceRequest(10000L);
                        chargeUserBalanceUseCase.execute(testUser.getUserId(), request);
                    } else {
                        // 차감 (간접적으로 User 엔티티 수정)
                        User user = userRepository.findById(testUser.getUserId()).get();
                        user.deductBalance(10000);
                        userRepository.save(user);
                    }
                } catch (Exception e) {
                    System.out.println("실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        entityManager.clear();
        User updatedUser = userRepository.findById(testUser.getUserId()).get();
        long finalBalance = updatedUser.getBalance().amount();

        long expectedBalance = 50000L;  // 초기 50000 + (10000 * 10) - (10000 * 10) = 50000

        System.out.println("=== 충전/차감 동시 실행 테스트 결과 ===");
        System.out.println("초기 잔액: 50000원");
        System.out.println("충전: +10000원 * 10번");
        System.out.println("차감: -10000원 * 10번");
        System.out.println("예상 잔액: " + expectedBalance + "원");
        System.out.println("실제 잔액: " + finalBalance + "원");
        System.out.println("차이: " + (finalBalance - expectedBalance) + "원");

        // 동시성 제어가 없다면:
        // - 충전과 차감이 서로 덮어쓰면서 예상과 다른 결과 발생

        assertThat(finalBalance).isEqualTo(expectedBalance);
    }
}
