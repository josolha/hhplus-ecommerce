package com.sparta.ecommerce.domain.coupon;

import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserCoupon의 use() 메서드가 Dirty Checking으로 제대로 업데이트되는지 테스트
 */
@SpringBootTest
class UserCouponUpdateTest {

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @Transactional
    @DisplayName("트랜잭션 내에서 UserCoupon.use() 호출 시 save() 없이도 usedAt이 업데이트되어야 함")
    void testUserCouponUseDirtyChecking() {
        // Given: 쿠폰 생성
        Coupon coupon = Coupon.builder()
                .name("테스트 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(1000L)
                .minOrderAmount(10000L)
                .stock(new com.sparta.ecommerce.domain.coupon.vo.CouponStock(100, 0, 100))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // Given: 사용자 쿠폰 발급
        UserCoupon userCoupon = UserCoupon.issue("user123", savedCoupon);
        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);
        String userCouponId = savedUserCoupon.getUserCouponId();

        System.out.println("=== 쿠폰 발급 직후 ===");
        System.out.println("usedAt: " + savedUserCoupon.getUsedAt());

        // When: use() 호출 (save() 없이)
        savedUserCoupon.use();
        System.out.println("\n=== use() 호출 직후 (save 전) ===");
        System.out.println("usedAt: " + savedUserCoupon.getUsedAt());

        // save() 하지 않음!
        // userCouponRepository.save(savedUserCoupon); // 이 줄 없음!

        // Then: 트랜잭션 커밋 후 DB에서 다시 조회하여 확인
        // (트랜잭션이 끝나면 flush되어 DB에 반영됨)
    }

    @Test
    @Transactional
    @DisplayName("트랜잭션 내에서 UserCoupon.use() 호출 후 flush하면 즉시 DB 반영")
    void testUserCouponUseWithFlush() {
        // Given: 쿠폰 생성
        Coupon coupon = Coupon.builder()
                .name("테스트 쿠폰2")
                .discountType(DiscountType.FIXED)
                .discountValue(1000L)
                .minOrderAmount(10000L)
                .stock(new com.sparta.ecommerce.domain.coupon.vo.CouponStock(100, 0, 100))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        Coupon savedCoupon = couponRepository.save(coupon);

        // Given: 사용자 쿠폰 발급
        UserCoupon userCoupon = UserCoupon.issue("user456", savedCoupon);
        UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);
        String userCouponId = savedUserCoupon.getUserCouponId();

        System.out.println("\n=== 쿠폰 발급 직후 ===");
        System.out.println("usedAt: " + savedUserCoupon.getUsedAt());
        assertThat(savedUserCoupon.getUsedAt()).isNull();

        // When: use() 호출 후 flush
        savedUserCoupon.use();
        userCouponRepository.flush();  // 강제로 DB에 반영

        System.out.println("\n=== use() 호출 + flush 직후 ===");
        System.out.println("usedAt: " + savedUserCoupon.getUsedAt());

        // Then: 영속성 컨텍스트를 clear하고 다시 조회
        userCouponRepository.flush();

        UserCoupon reloaded = userCouponRepository.findById(userCouponId).orElseThrow();
        System.out.println("\n=== DB에서 다시 조회 ===");
        System.out.println("usedAt: " + reloaded.getUsedAt());

        // 검증: usedAt이 null이 아니어야 함
        assertThat(reloaded.getUsedAt()).isNotNull();
        assertThat(reloaded.isUsed()).isTrue();
    }
}
