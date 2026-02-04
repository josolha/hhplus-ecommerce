package com.sparta.ecommerce.presentation.controller.coupon;

import com.sparta.ecommerce.application.coupon.usecase.GetAvailableCouponsUseCase;
import com.sparta.ecommerce.application.coupon.usecase.CreateCouponUseCase;
import com.sparta.ecommerce.application.coupon.usecase.IssueCouponWithQueueUseCase;
import com.sparta.ecommerce.application.coupon.usecase.ValidateCouponUseCase;
import com.sparta.ecommerce.application.coupon.dto.CouponResponse;
import com.sparta.ecommerce.domain.coupon.DiscountType;
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
 * 쿠폰 컨트롤러 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 컨트롤러 테스트")
class CouponControllerTest {

    @Mock
    private CreateCouponUseCase createCouponUseCase;

    @Mock
    private GetAvailableCouponsUseCase getAvailableCouponsUseCase;

    @Mock
    private IssueCouponWithQueueUseCase issueCouponWithQueueUseCase;

    @Mock
    private ValidateCouponUseCase validateCouponUseCase;

    @InjectMocks
    private CouponController couponController;

    @Test
    @DisplayName("GET /api/coupons - 발급 가능한 쿠폰 목록을 조회한다")
    void 쿠폰_목록_조회_성공() {
        // given
        LocalDateTime expiresAt = LocalDateTime.of(2025, 12, 31, 23, 59, 59);
        List<CouponResponse> coupons = List.of(
                new CouponResponse(
                        "C001",
                        "신규 가입 5만원 할인 쿠폰",
                        DiscountType.FIXED,
                        50000,
                        100,
                        50,
                        100000,
                        expiresAt
                ),
                new CouponResponse(
                        "C002",
                        "10% 할인 쿠폰",
                        DiscountType.PERCENT,
                        10,
                        200,
                        150,
                        50000,
                        expiresAt
                ),
                new CouponResponse(
                        "C003",
                        "VIP 20만원 할인 쿠폰",
                        DiscountType.FIXED,
                        200000,
                        50,
                        10,
                        1000000,
                        expiresAt
                )
        );

        given(getAvailableCouponsUseCase.execute()).willReturn(coupons);

        // when
        var result = couponController.getCoupons();

        // then
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(3);
        assertThat(result.getBody().get(0).couponId()).isEqualTo("C001");
        assertThat(result.getBody().get(0).name()).isEqualTo("신규 가입 5만원 할인 쿠폰");
        assertThat(result.getBody().get(0).discountType()).isEqualTo(DiscountType.FIXED);
        assertThat(result.getBody().get(0).discountValue()).isEqualTo(50000);

        verify(getAvailableCouponsUseCase, times(1)).execute();
    }

    @Test
    @DisplayName("GET /api/coupons - 발급 가능한 쿠폰이 없으면 빈 배열을 반환한다")
    void 쿠폰_목록_조회_빈_결과() {
        // given
        given(getAvailableCouponsUseCase.execute()).willReturn(List.of());

        // when
        var result = couponController.getCoupons();

        // then
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();

        verify(getAvailableCouponsUseCase, times(1)).execute();
    }

    @Test
    @DisplayName("GET /api/coupons - FIXED 타입과 PERCENT 타입 쿠폰이 모두 조회된다")
    void 다양한_할인_타입_쿠폰_조회() {
        // given
        LocalDateTime expiresAt = LocalDateTime.of(2025, 12, 31, 23, 59, 59);
        List<CouponResponse> coupons = List.of(
                new CouponResponse(
                        "C001",
                        "정액 할인 쿠폰",
                        DiscountType.FIXED,
                        50000,
                        100,
                        50,
                        100000,
                        expiresAt
                ),
                new CouponResponse(
                        "C002",
                        "정률 할인 쿠폰",
                        DiscountType.PERCENT,
                        10,
                        200,
                        150,
                        50000,
                        expiresAt
                )
        );

        given(getAvailableCouponsUseCase.execute()).willReturn(coupons);

        // when
        var result = couponController.getCoupons();

        // then
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).discountType()).isEqualTo(DiscountType.FIXED);
        assertThat(result.getBody().get(1).discountType()).isEqualTo(DiscountType.PERCENT);

        verify(getAvailableCouponsUseCase, times(1)).execute();
    }
}
