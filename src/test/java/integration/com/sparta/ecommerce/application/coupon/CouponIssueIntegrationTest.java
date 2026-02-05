package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.service.CouponIssueRedisService;
import com.sparta.ecommerce.application.coupon.usecase.IssueCouponWithQueueUseCase;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class CouponIssueIntegrationTest {

    // Kafka Consumer를 Mock으로 대체하여 비활성화
    @MockBean
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private IssueCouponWithQueueUseCase issueCouponUseCase;

    @Autowired
    private CouponIssueRedisService redisService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 쿠폰 초기화 헬퍼 메서드
     * 각 테스트마다 다른 쿠폰 ID를 사용하여 병렬 실행 시 간섭 방지
     *
     * 주의: Kafka Consumer가 비활성화되어 있으므로 DB 쿠폰 생성은 필요 없지만,
     * 향후 Consumer 활성화를 대비해 생성은 유지
     */
    private void setupCoupon(String couponId, int stock) {
        // Redis 초기화
        redisTemplate.delete("coupon:issued:" + couponId);
        redisTemplate.delete("coupon:stock:" + couponId);
        redisTemplate.delete("coupon:sold-out:" + couponId);

        // Redis 재고 설정
        redisService.initializeStock(couponId, stock);

        System.out.println("=== 테스트 준비 완료: couponId=" + couponId + ", 재고=" + stock + "개 (Redis) ===");
    }

    @Test
    @DisplayName("정상 발급: 5명이 순차적으로 발급 성공")
    void successfulIssue() {
        // given
        String couponId = "test-coupon-successful";
        setupCoupon(couponId, 5);
        String[] users = {"user1", "user2", "user3", "user4", "user5"};

        // when & then
        for (int i = 0; i < users.length; i++) {
            String userId = users[i];

            // 발급 성공
            var response = issueCouponUseCase.execute(userId, couponId);
            assertThat(response.queued()).isTrue();

            System.out.println((i + 1) + "번째 발급 성공: " + userId);
        }

        // 재고 확인
        String stock = redisTemplate.opsForValue().get("coupon:stock:" + couponId);
        assertThat(stock).isEqualTo("0");
        System.out.println("=== 최종 재고: " + stock + " ===");
    }

    @Test
    @DisplayName("중복 발급 차단: 같은 사용자가 2번 요청 시 409 에러")
    void duplicateIssue() {
        // given
        String couponId = "test-coupon-duplicate";
        setupCoupon(couponId, 5);
        String userId = "user1";

        // when
        // 첫 번째 요청: 성공
        var response1 = issueCouponUseCase.execute(userId, couponId);
        assertThat(response1.queued()).isTrue();
        System.out.println("첫 번째 발급 성공: " + userId);

        // then
        // 두 번째 요청: 중복 에러
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(DuplicateCouponIssueException.class);
        System.out.println("두 번째 발급 차단 (중복): " + userId);

        // 재고 확인 (1개만 감소)
        String stock = redisTemplate.opsForValue().get("coupon:stock:" + couponId);
        assertThat(stock).isEqualTo("4");
        System.out.println("=== 최종 재고: " + stock + " (1개만 감소) ===");
    }

    @Test
    @DisplayName("품절: 5개 발급 후 6번째 요청 시 400 에러")
    void soldOut() {
        // given
        String couponId = "test-coupon-soldout";
        setupCoupon(couponId, 5);
        String[] users = {"user1", "user2", "user3", "user4", "user5"};

        // when
        // 5명 발급
        for (String userId : users) {
            issueCouponUseCase.execute(userId, couponId);
            System.out.println("발급 성공: " + userId);
        }

        // 재고 확인
        String stock = redisTemplate.opsForValue().get("coupon:stock:" + couponId);
        assertThat(stock).isEqualTo("0");
        System.out.println("재고 소진: " + stock);

        // then
        // 6번째 요청: 품절 에러
        assertThatThrownBy(() -> issueCouponUseCase.execute("user6", couponId))
                .isInstanceOf(CouponSoldOutException.class);
        System.out.println("6번째 발급 차단 (품절): user6");

        // 재고 확인 (0 유지)
        stock = redisTemplate.opsForValue().get("coupon:stock:" + couponId);
        assertThat(stock).isEqualTo("0");
        System.out.println("=== 최종 재고: " + stock + " ===");
    }

    @Test
    @DisplayName("복합 시나리오: 정상 + 중복 + 품절")
    void complexScenario() {
        // given
        String couponId = "test-coupon-complex";
        setupCoupon(couponId, 5);

        // 1. user1, user2, user3 발급 (성공)
        issueCouponUseCase.execute("user1", couponId);
        issueCouponUseCase.execute("user2", couponId);
        issueCouponUseCase.execute("user3", couponId);
        System.out.println("3명 발급 성공");

        // 2. user1 재요청 (중복 에러)
        assertThatThrownBy(() -> issueCouponUseCase.execute("user1", couponId))
                .isInstanceOf(DuplicateCouponIssueException.class);
        System.out.println("user1 중복 차단");

        // 3. user4, user5 발급 (재고 소진)
        issueCouponUseCase.execute("user4", couponId);
        issueCouponUseCase.execute("user5", couponId);
        System.out.println("user4, user5 발급 성공 (재고 소진)");

        // 4. user6 요청 (품절 에러)
        assertThatThrownBy(() -> issueCouponUseCase.execute("user6", couponId))
                .isInstanceOf(CouponSoldOutException.class);
        System.out.println("user6 품절 차단");

        // 최종 재고 확인
        String stock = redisTemplate.opsForValue().get("coupon:stock:" + couponId);
        assertThat(stock).isEqualTo("0");
        System.out.println("=== 최종 재고: " + stock + " ===");
    }
}
