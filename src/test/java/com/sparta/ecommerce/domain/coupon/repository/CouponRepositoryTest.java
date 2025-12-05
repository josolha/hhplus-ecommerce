package com.sparta.ecommerce.domain.coupon.repository;

import com.sparta.ecommerce.IntegrationTestBase;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coupon Repository 통합 테스트
 * Testcontainers를 사용하여 실제 MySQL DB 제약조건 및 재고 관리 검증
 */
@DisplayName("Coupon Repository 통합 테스트")
public class CouponRepositoryTest extends IntegrationTestBase {

    @Autowired
    private CouponRepository couponRepository;

    @Test
    @DisplayName("Coupon 저장 및 조회 - JPA ID 자동 생성")
    void saveAndFind() {
        // given
        Coupon coupon = Coupon.builder()
                .name("5000원 할인쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(100, 0, 100))
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(savedCoupon.getCouponId()).isNotNull();  // ID 자동 생성 검증

        Coupon foundCoupon = couponRepository.findById(savedCoupon.getCouponId()).get();
        assertThat(foundCoupon.getName()).isEqualTo("5000원 할인쿠폰");
        assertThat(foundCoupon.getDiscountType()).isEqualTo(DiscountType.FIXED);
        assertThat(foundCoupon.getDiscountValue()).isEqualTo(5000L);
        assertThat(foundCoupon.getStock().getTotalQuantity()).isEqualTo(100);
        assertThat(foundCoupon.getStock().getRemainingQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Coupon discountType NULL 시 에러 - DB 제약조건 검증")
    void nullDiscountType() {
        // given
        Coupon coupon = Coupon.builder()
                .name("잘못된쿠폰")
                // discountType 누락!
                .discountValue(5000L)
                .stock(new CouponStock(100, 0, 100))
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // when & then
        assertThatThrownBy(() -> {
            couponRepository.save(coupon);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Coupon 재고 감소 - 업데이트 검증")
    void decreaseStock() {
        // given
        Coupon coupon = Coupon.builder()
                .name("재고감소쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(100, 0, 100))
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        couponRepository.save(coupon);
        entityManager.flush();
        entityManager.clear();

        // when
        Coupon foundCoupon = couponRepository.findById(coupon.getCouponId()).get();
        CouponStock newStock = foundCoupon.getStock().issue();  // 1개 발급
        Coupon updatedCoupon = Coupon.builder()
                .couponId(foundCoupon.getCouponId())
                .name(foundCoupon.getName())
                .discountType(foundCoupon.getDiscountType())
                .discountValue(foundCoupon.getDiscountValue())
                .stock(newStock)
                .minOrderAmount(foundCoupon.getMinOrderAmount())
                .expiresAt(foundCoupon.getExpiresAt())
                .build();
        couponRepository.save(updatedCoupon);
        entityManager.flush();
        entityManager.clear();

        // then
        Coupon result = couponRepository.findById(coupon.getCouponId()).get();
        assertThat(result.getStock().getRemainingQuantity()).isEqualTo(99);
        assertThat(result.getStock().getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Coupon 퍼센트 할인 타입 저장")
    void percentDiscountType() {
        // given
        Coupon coupon = Coupon.builder()
                .name("10% 할인쿠폰")
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)  // 10%
                .stock(new CouponStock(200, 0, 200))
                .minOrderAmount(50000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);
        entityManager.flush();
        entityManager.clear();

        // then
        Coupon foundCoupon = couponRepository.findById(savedCoupon.getCouponId()).get();
        assertThat(foundCoupon.getDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(foundCoupon.getDiscountValue()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Coupon 만료일 검증")
    void expiresAt() {
        // given
        LocalDateTime futureDate = LocalDateTime.now().plusDays(30);
        Coupon coupon = Coupon.builder()
                .name("만료일쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(100, 0, 100))
                .minOrderAmount(10000L)
                .expiresAt(futureDate)
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);
        entityManager.flush();
        entityManager.clear();

        // then
        Coupon foundCoupon = couponRepository.findById(savedCoupon.getCouponId()).get();
        assertThat(foundCoupon.getExpiresAt()).isNotNull();
        assertThat(foundCoupon.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Coupon 만료된 쿠폰 검증")
    void expiredCoupon() {
        // given
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        Coupon coupon = Coupon.builder()
                .name("만료된쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(100, 0, 100))
                .minOrderAmount(10000L)
                .expiresAt(pastDate)  // 어제 만료
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);
        entityManager.flush();
        entityManager.clear();

        // then
        Coupon foundCoupon = couponRepository.findById(savedCoupon.getCouponId()).get();
        assertThat(foundCoupon.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Coupon 재고 0일 때 저장 가능")
    void zeroStock() {
        // given
        Coupon coupon = Coupon.builder()
                .name("품절쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(100, 100, 0))  // 재고 0
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);
        entityManager.flush();
        entityManager.clear();

        // then
        Coupon foundCoupon = couponRepository.findById(savedCoupon.getCouponId()).get();
        assertThat(foundCoupon.getStock().getRemainingQuantity()).isEqualTo(0);
        assertThat(foundCoupon.getStock().isOutOfStock()).isTrue();
    }

    @Test
    @DisplayName("Coupon 최소 주문 금액 검증")
    void minOrderAmount() {
        // given
        Coupon coupon = Coupon.builder()
                .name("최소금액쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(100, 0, 100))
                .minOrderAmount(50000L)  // 5만원 이상
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);
        entityManager.flush();
        entityManager.clear();

        // then
        Coupon foundCoupon = couponRepository.findById(savedCoupon.getCouponId()).get();
        assertThat(foundCoupon.getMinOrderAmount()).isEqualTo(50000L);
        assertThat(foundCoupon.meetsMinOrderAmount(60000)).isTrue();
        assertThat(foundCoupon.meetsMinOrderAmount(40000)).isFalse();
    }

    @Test
    @DisplayName("Coupon 조회 시 createdAt/updatedAt 자동 생성 검증")
    void auditFields() {
        // given
        Coupon coupon = Coupon.builder()
                .name("감사필드쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(5000L)
                .stock(new CouponStock(100, 0, 100))
                .minOrderAmount(10000L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // when
        Coupon savedCoupon = couponRepository.save(coupon);
        entityManager.flush();
        entityManager.clear();

        // then
        Coupon foundCoupon = couponRepository.findById(savedCoupon.getCouponId()).get();
        assertThat(foundCoupon.getCreatedAt()).isNotNull();
        assertThat(foundCoupon.getUpdatedAt()).isNotNull();
    }
}
