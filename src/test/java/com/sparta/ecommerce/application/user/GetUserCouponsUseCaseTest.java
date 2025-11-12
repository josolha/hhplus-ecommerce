package com.sparta.ecommerce.application.user;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.CouponStatus;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 사용자 쿠폰 목록 조회 UseCase 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 쿠폰 목록 조회 UseCase 테스트")
class GetUserCouponsUseCaseTest {

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private GetUserCouponsUseCase getUserCouponsUseCase;

    @Test
    @DisplayName("사용자의 전체 쿠폰 목록 조회 성공 (status 필터 없음)")
    void 전체_쿠폰_조회_성공() {
        // given
        String userId = "U001";

        UserCoupon userCoupon1 = UserCoupon.builder()
                .userCouponId("UC001")
                .userId(userId)
                .couponId("C001")
                .issuedAt(LocalDateTime.now().minusDays(5))
                .usedAt(null)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        UserCoupon userCoupon2 = UserCoupon.builder()
                .userCouponId("UC002")
                .userId(userId)
                .couponId("C002")
                .issuedAt(LocalDateTime.now().minusDays(3))
                .usedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        Coupon coupon1 = Coupon.builder()
                .couponId("C001")
                .name("5만원 할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(100, 50))
                .minOrderAmount(100000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        Coupon coupon2 = Coupon.builder()
                .couponId("C002")
                .name("10% 할인 쿠폰")
                .discountType(DiscountType.PERCENT)
                .discountValue(10)
                .stock(new CouponStock(200, 150))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(userCouponRepository.findByUserId(userId)).willReturn(List.of(userCoupon1, userCoupon2));
        given(couponRepository.findById("C001")).willReturn(Optional.of(coupon1));
        given(couponRepository.findById("C002")).willReturn(Optional.of(coupon2));

        // when
        List<UserCouponResponse> responses = getUserCouponsUseCase.execute(userId, null);

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).userCouponId()).isEqualTo("UC001");
        assertThat(responses.get(0).name()).isEqualTo("5만원 할인 쿠폰");
        assertThat(responses.get(1).userCouponId()).isEqualTo("UC002");
        assertThat(responses.get(1).name()).isEqualTo("10% 할인 쿠폰");
    }

    @Test
    @DisplayName("AVAILABLE 상태의 쿠폰만 조회")
    void AVAILABLE_쿠폰만_조회() {
        // given
        String userId = "U001";

        UserCoupon availableCoupon = UserCoupon.builder()
                .userCouponId("UC001")
                .userId(userId)
                .couponId("C001")
                .issuedAt(LocalDateTime.now().minusDays(5))
                .usedAt(null)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        UserCoupon usedCoupon = UserCoupon.builder()
                .userCouponId("UC002")
                .userId(userId)
                .couponId("C002")
                .issuedAt(LocalDateTime.now().minusDays(3))
                .usedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        Coupon coupon1 = Coupon.builder()
                .couponId("C001")
                .name("사용 가능한 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(100, 50))
                .minOrderAmount(100000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        Coupon coupon2 = Coupon.builder()
                .couponId("C002")
                .name("사용한 쿠폰")
                .discountType(DiscountType.PERCENT)
                .discountValue(10)
                .stock(new CouponStock(200, 150))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(userCouponRepository.findByUserId(userId)).willReturn(List.of(availableCoupon, usedCoupon));
        given(couponRepository.findById("C001")).willReturn(Optional.of(coupon1));

        // when
        List<UserCouponResponse> responses = getUserCouponsUseCase.execute(userId, CouponStatus.AVAILABLE);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userCouponId()).isEqualTo("UC001");
        assertThat(responses.get(0).usedAt()).isNull();
    }

    @Test
    @DisplayName("USED 상태의 쿠폰만 조회")
    void USED_쿠폰만_조회() {
        // given
        String userId = "U001";

        UserCoupon usedCoupon = UserCoupon.builder()
                .userCouponId("UC002")
                .userId(userId)
                .couponId("C002")
                .issuedAt(LocalDateTime.now().minusDays(3))
                .usedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        UserCoupon availableCoupon = UserCoupon.builder()
                .userCouponId("UC001")
                .userId(userId)
                .couponId("C001")
                .issuedAt(LocalDateTime.now().minusDays(5))
                .usedAt(null)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        Coupon coupon2 = Coupon.builder()
                .couponId("C002")
                .name("사용한 쿠폰")
                .discountType(DiscountType.PERCENT)
                .discountValue(10)
                .stock(new CouponStock(200, 150))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(userCouponRepository.findByUserId(userId)).willReturn(List.of(usedCoupon, availableCoupon));
        given(couponRepository.findById("C002")).willReturn(Optional.of(coupon2));

        // when
        List<UserCouponResponse> responses = getUserCouponsUseCase.execute(userId, CouponStatus.USED);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userCouponId()).isEqualTo("UC002");
        assertThat(responses.get(0).usedAt()).isNotNull();
    }

    @Test
    @DisplayName("EXPIRED 상태의 쿠폰만 조회")
    void EXPIRED_쿠폰만_조회() {
        // given
        String userId = "U001";

        UserCoupon expiredCoupon = UserCoupon.builder()
                .userCouponId("UC003")
                .userId(userId)
                .couponId("C003")
                .issuedAt(LocalDateTime.now().minusMonths(2))
                .usedAt(null)
                .expiresAt(LocalDateTime.now().minusDays(1)) // 어제 만료
                .build();

        UserCoupon availableCoupon = UserCoupon.builder()
                .userCouponId("UC001")
                .userId(userId)
                .couponId("C001")
                .issuedAt(LocalDateTime.now().minusDays(5))
                .usedAt(null)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        Coupon coupon3 = Coupon.builder()
                .couponId("C003")
                .name("만료된 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(30000)
                .stock(new CouponStock(50, 10))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusMonths(2))
                .build();

        given(userCouponRepository.findByUserId(userId)).willReturn(List.of(expiredCoupon, availableCoupon));
        given(couponRepository.findById("C003")).willReturn(Optional.of(coupon3));

        // when
        List<UserCouponResponse> responses = getUserCouponsUseCase.execute(userId, CouponStatus.EXPIRED);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userCouponId()).isEqualTo("UC003");
        assertThat(responses.get(0).expiresAt()).isBefore(LocalDateTime.now());
    }

    @Test
    @DisplayName("사용자가 보유한 쿠폰이 없는 경우 빈 리스트 반환")
    void 쿠폰_없음() {
        // given
        String userId = "U999";

        given(userCouponRepository.findByUserId(userId)).willReturn(List.of());

        // when
        List<UserCouponResponse> responses = getUserCouponsUseCase.execute(userId, null);

        // then
        assertThat(responses).isEmpty();
    }
}
