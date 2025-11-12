package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.CouponResponse;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 발급 가능한 쿠폰 목록 조회 UseCase 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("발급 가능한 쿠폰 목록 조회 UseCase 테스트")
class GetAvailableCouponsUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private GetAvailableCouponsUseCase getAvailableCouponsUseCase;

    private List<Coupon> availableCoupons;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        // 발급 가능한 쿠폰들 (재고 있고 만료되지 않음)
        availableCoupons = List.of(
                Coupon.builder()
                        .couponId("C001")
                        .name("신규 가입 5만원 할인 쿠폰")
                        .discountType(DiscountType.FIXED)
                        .discountValue(50000)
                        .stock(new CouponStock(100, 50))
                        .minOrderAmount(100000)
                        .expiresAt(now.plusMonths(2))
                        .createdAt(now)
                        .build(),
                Coupon.builder()
                        .couponId("C002")
                        .name("10% 할인 쿠폰")
                        .discountType(DiscountType.PERCENT)
                        .discountValue(10)
                        .stock(new CouponStock(200, 150))
                        .minOrderAmount(50000)
                        .expiresAt(now.plusMonths(1))
                        .createdAt(now)
                        .build(),
                Coupon.builder()
                        .couponId("C003")
                        .name("VIP 20만원 할인 쿠폰")
                        .discountType(DiscountType.FIXED)
                        .discountValue(200000)
                        .stock(new CouponStock(50, 10))
                        .minOrderAmount(1000000)
                        .expiresAt(now.plusMonths(3))
                        .createdAt(now)
                        .build()
        );
    }

    @Test
    @DisplayName("발급 가능한 쿠폰 목록을 조회한다")
    void 발급_가능한_쿠폰_목록_조회_성공() {
        // given
        given(couponRepository.findAvailableCoupons()).willReturn(availableCoupons);

        // when
        List<CouponResponse> responses = getAvailableCouponsUseCase.execute();

        // then
        assertThat(responses).hasSize(3);
        assertThat(responses).extracting("name")
                .containsExactlyInAnyOrder(
                        "신규 가입 5만원 할인 쿠폰",
                        "10% 할인 쿠폰",
                        "VIP 20만원 할인 쿠폰"
                );

        verify(couponRepository, times(1)).findAvailableCoupons();
    }

    @Test
    @DisplayName("쿠폰 응답에 모든 필드가 정확하게 매핑된다")
    void 쿠폰_응답_필드_매핑_확인() {
        // given
        given(couponRepository.findAvailableCoupons()).willReturn(availableCoupons);

        // when
        List<CouponResponse> responses = getAvailableCouponsUseCase.execute();

        // then
        CouponResponse firstCoupon = responses.get(0);
        assertThat(firstCoupon.couponId()).isEqualTo("C001");
        assertThat(firstCoupon.name()).isEqualTo("신규 가입 5만원 할인 쿠폰");
        assertThat(firstCoupon.discountType()).isEqualTo(DiscountType.FIXED);
        assertThat(firstCoupon.discountValue()).isEqualTo(50000);
        assertThat(firstCoupon.totalQuantity()).isEqualTo(100);
        assertThat(firstCoupon.remainingQuantity()).isEqualTo(50);
        assertThat(firstCoupon.minOrderAmount()).isEqualTo(100000);
        assertThat(firstCoupon.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("FIXED 타입과 PERCENT 타입 쿠폰이 모두 조회된다")
    void 다양한_할인_타입_쿠폰_조회() {
        // given
        given(couponRepository.findAvailableCoupons()).willReturn(availableCoupons);

        // when
        List<CouponResponse> responses = getAvailableCouponsUseCase.execute();

        // then
        long fixedCount = responses.stream()
                .filter(r -> r.discountType() == DiscountType.FIXED)
                .count();
        long percentCount = responses.stream()
                .filter(r -> r.discountType() == DiscountType.PERCENT)
                .count();

        assertThat(fixedCount).isEqualTo(2);
        assertThat(percentCount).isEqualTo(1);
    }

    @Test
    @DisplayName("발급 가능한 쿠폰이 없으면 빈 리스트를 반환한다")
    void 발급_가능한_쿠폰_없음() {
        // given
        given(couponRepository.findAvailableCoupons()).willReturn(List.of());

        // when
        List<CouponResponse> responses = getAvailableCouponsUseCase.execute();

        // then
        assertThat(responses).isEmpty();
        verify(couponRepository, times(1)).findAvailableCoupons();
    }

    @Test
    @DisplayName("쿠폰의 남은 수량이 정확하게 조회된다")
    void 남은_수량_확인() {
        // given
        given(couponRepository.findAvailableCoupons()).willReturn(availableCoupons);

        // when
        List<CouponResponse> responses = getAvailableCouponsUseCase.execute();

        // then
        assertThat(responses).allMatch(r -> r.remainingQuantity() > 0);

        CouponResponse c001 = responses.stream()
                .filter(r -> r.couponId().equals("C001"))
                .findFirst()
                .orElseThrow();
        assertThat(c001.remainingQuantity()).isEqualTo(50);

        CouponResponse c002 = responses.stream()
                .filter(r -> r.couponId().equals("C002"))
                .findFirst()
                .orElseThrow();
        assertThat(c002.remainingQuantity()).isEqualTo(150);

        CouponResponse c003 = responses.stream()
                .filter(r -> r.couponId().equals("C003"))
                .findFirst()
                .orElseThrow();
        assertThat(c003.remainingQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("최소 주문 금액 정보가 정확하게 조회된다")
    void 최소_주문_금액_확인() {
        // given
        given(couponRepository.findAvailableCoupons()).willReturn(availableCoupons);

        // when
        List<CouponResponse> responses = getAvailableCouponsUseCase.execute();

        // then
        CouponResponse c001 = responses.stream()
                .filter(r -> r.couponId().equals("C001"))
                .findFirst()
                .orElseThrow();
        assertThat(c001.minOrderAmount()).isEqualTo(100000);

        CouponResponse c003 = responses.stream()
                .filter(r -> r.couponId().equals("C003"))
                .findFirst()
                .orElseThrow();
        assertThat(c003.minOrderAmount()).isEqualTo(1000000);
    }
}
