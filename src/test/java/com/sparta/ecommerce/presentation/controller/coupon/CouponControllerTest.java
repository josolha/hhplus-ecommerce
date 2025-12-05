package com.sparta.ecommerce.presentation.controller.coupon;

import com.sparta.ecommerce.application.coupon.usecase.GetAvailableCouponsUseCase;
import com.sparta.ecommerce.application.coupon.usecase.IssueCouponUseCase;
import com.sparta.ecommerce.application.coupon.usecase.ValidateCouponUseCase;
import com.sparta.ecommerce.application.coupon.dto.CouponResponse;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쿠폰 컨트롤러 테스트
 */
@WebMvcTest(CouponController.class)
@DisplayName("쿠폰 컨트롤러 테스트")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetAvailableCouponsUseCase getAvailableCouponsUseCase;

    @MockBean
    private IssueCouponUseCase issueCouponUseCase;

    @MockBean
    private ValidateCouponUseCase validateCouponUseCase;

    @Test
    @DisplayName("GET /api/coupons - 발급 가능한 쿠폰 목록을 조회한다")
    void 쿠폰_목록_조회_성공() throws Exception {
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

        // when & then
        mockMvc.perform(get("/api/coupons"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].couponId", is("C001")))
                .andExpect(jsonPath("$[0].name", is("신규 가입 5만원 할인 쿠폰")))
                .andExpect(jsonPath("$[0].discountType", is("FIXED")))
                .andExpect(jsonPath("$[0].discountValue", is(50000)))
                .andExpect(jsonPath("$[0].totalQuantity", is(100)))
                .andExpect(jsonPath("$[0].remainingQuantity", is(50)))
                .andExpect(jsonPath("$[0].minOrderAmount", is(100000)))
                .andExpect(jsonPath("$[1].couponId", is("C002")))
                .andExpect(jsonPath("$[1].name", is("10% 할인 쿠폰")))
                .andExpect(jsonPath("$[1].discountType", is("PERCENT")))
                .andExpect(jsonPath("$[1].discountValue", is(10)))
                .andExpect(jsonPath("$[2].couponId", is("C003")))
                .andExpect(jsonPath("$[2].name", is("VIP 20만원 할인 쿠폰")));

        verify(getAvailableCouponsUseCase, times(1)).execute();
    }

    @Test
    @DisplayName("GET /api/coupons - 발급 가능한 쿠폰이 없으면 빈 배열을 반환한다")
    void 쿠폰_목록_조회_빈_결과() throws Exception {
        // given
        given(getAvailableCouponsUseCase.execute()).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/coupons"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)))
                .andExpect(jsonPath("$", is(empty())));

        verify(getAvailableCouponsUseCase, times(1)).execute();
    }

    @Test
    @DisplayName("GET /api/coupons - FIXED 타입과 PERCENT 타입 쿠폰이 모두 조회된다")
    void 다양한_할인_타입_쿠폰_조회() throws Exception {
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

        // when & then
        mockMvc.perform(get("/api/coupons"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].discountType", is("FIXED")))
                .andExpect(jsonPath("$[1].discountType", is("PERCENT")));

        verify(getAvailableCouponsUseCase, times(1)).execute();
    }
}
