package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.ValidateCouponRequest;
import com.sparta.ecommerce.application.coupon.dto.ValidateCouponResponse;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 쿠폰 유효성 검증 UseCase 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 유효성 검증 UseCase 테스트")
class ValidateCouponUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private ValidateCouponUseCase validateCouponUseCase;

    @Test
    @DisplayName("유효한 쿠폰으로 검증 성공 - FIXED 타입")
    void 정액_할인_쿠폰_검증_성공() {
        // given
        String couponId = "C001";
        int orderAmount = 150000;

        Coupon coupon = Coupon.builder()
                .couponId(couponId)
                .name("5만원 할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(100, 50))
                .minOrderAmount(100000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        ValidateCouponRequest request = new ValidateCouponRequest(couponId, orderAmount);

        // when
        ValidateCouponResponse response = validateCouponUseCase.execute(request);

        // then
        assertThat(response.valid()).isTrue();
        assertThat(response.discountAmount()).isEqualTo(50000);
        assertThat(response.finalAmount()).isEqualTo(100000); // 150000 - 50000
        assertThat(response.message()).contains("정상적으로 적용");
    }

    @Test
    @DisplayName("유효한 쿠폰으로 검증 성공 - PERCENT 타입")
    void 정률_할인_쿠폰_검증_성공() {
        // given
        String couponId = "C002";
        int orderAmount = 100000;

        Coupon coupon = Coupon.builder()
                .couponId(couponId)
                .name("10% 할인 쿠폰")
                .discountType(DiscountType.PERCENT)
                .discountValue(10)
                .stock(new CouponStock(200, 150))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        ValidateCouponRequest request = new ValidateCouponRequest(couponId, orderAmount);

        // when
        ValidateCouponResponse response = validateCouponUseCase.execute(request);

        // then
        assertThat(response.valid()).isTrue();
        assertThat(response.discountAmount()).isEqualTo(10000); // 100000 * 10%
        assertThat(response.finalAmount()).isEqualTo(90000);
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰으로 검증 실패")
    void 존재하지_않는_쿠폰() {
        // given
        String couponId = "INVALID";
        ValidateCouponRequest request = new ValidateCouponRequest(couponId, 100000);

        given(couponRepository.findById(couponId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> validateCouponUseCase.execute(request))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessage("존재하지 않는 쿠폰입니다");
    }

    @Test
    @DisplayName("만료된 쿠폰으로 검증 실패")
    void 만료된_쿠폰() {
        // given
        String couponId = "C003";
        Coupon expiredCoupon = Coupon.builder()
                .couponId(couponId)
                .name("만료된 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(100, 50))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().minusDays(1)) // 어제 만료
                .createdAt(LocalDateTime.now().minusMonths(1))
                .build();

        given(couponRepository.findById(couponId)).willReturn(Optional.of(expiredCoupon));

        ValidateCouponRequest request = new ValidateCouponRequest(couponId, 100000);

        // when
        ValidateCouponResponse response = validateCouponUseCase.execute(request);

        // then
        assertThat(response.valid()).isFalse();
        assertThat(response.discountAmount()).isEqualTo(0);
        assertThat(response.message()).contains("만료된 쿠폰");
    }

    @Test
    @DisplayName("최소 주문 금액 미만으로 검증 실패")
    void 최소_주문_금액_미만() {
        // given
        String couponId = "C001";
        int orderAmount = 50000; // 최소 주문 금액 100000원보다 적음

        Coupon coupon = Coupon.builder()
                .couponId(couponId)
                .name("5만원 할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(100, 50))
                .minOrderAmount(100000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        ValidateCouponRequest request = new ValidateCouponRequest(couponId, orderAmount);

        // when
        ValidateCouponResponse response = validateCouponUseCase.execute(request);

        // then
        assertThat(response.valid()).isFalse();
        assertThat(response.discountAmount()).isEqualTo(0);
        assertThat(response.message()).contains("최소 주문 금액");
        assertThat(response.message()).contains("100000");
    }

    @Test
    @DisplayName("최소 주문 금액과 정확히 일치하면 검증 성공")
    void 최소_주문_금액_정확히_일치() {
        // given
        String couponId = "C001";
        int orderAmount = 100000;

        Coupon coupon = Coupon.builder()
                .couponId(couponId)
                .name("5만원 할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(100, 50))
                .minOrderAmount(100000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(couponRepository.findById(couponId)).willReturn(Optional.of(coupon));

        ValidateCouponRequest request = new ValidateCouponRequest(couponId, orderAmount);

        // when
        ValidateCouponResponse response = validateCouponUseCase.execute(request);

        // then
        assertThat(response.valid()).isTrue();
        assertThat(response.discountAmount()).isEqualTo(50000);
        assertThat(response.finalAmount()).isEqualTo(50000);
    }
}
