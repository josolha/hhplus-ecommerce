package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 쿠폰 발급 UseCase 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 발급 UseCase 테스트")
class IssueCouponUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private IssueCouponUseCase issueCouponUseCase;

    @Test
    @DisplayName("정상적인 쿠폰 발급 성공")
    void 쿠폰_발급_성공() {
        // given
        String userId = "U001";
        String couponId = "C001";

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

        given(couponRepository.findByIdWithLock(couponId)).willReturn(Optional.of(coupon));
        given(userCouponRepository.existsByUserIdAndCouponIdWithLock(userId, couponId)).willReturn(false);

        // when
        UserCouponResponse response = issueCouponUseCase.execute(userId, couponId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.name()).isEqualTo("5만원 할인 쿠폰");
        assertThat(response.discountType()).isEqualTo(DiscountType.FIXED);
        assertThat(response.discountValue()).isEqualTo(50000);
        assertThat(response.issuedAt()).isNotNull();
        assertThat(response.usedAt()).isNull();

        verify(couponRepository).save(any(Coupon.class));
        verify(userCouponRepository).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰으로 발급 실패")
    void 존재하지_않는_쿠폰() {
        // given
        String userId = "U001";
        String couponId = "INVALID";

        given(couponRepository.findByIdWithLock(couponId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessage("존재하지 않는 쿠폰입니다");
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰 중복 발급 실패")
    void 중복_발급_실패() {
        // given
        String userId = "U001";
        String couponId = "C001";

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

        given(couponRepository.findByIdWithLock(couponId)).willReturn(Optional.of(coupon));
        given(userCouponRepository.existsByUserIdAndCouponIdWithLock(userId, couponId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(DuplicateCouponIssueException.class)
                .hasMessageContaining("이미 발급받은 쿠폰입니다");
    }

    @Test
    @DisplayName("만료된 쿠폰 발급 실패")
    void 만료된_쿠폰() {
        // given
        String userId = "U001";
        String couponId = "C002";

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

        given(couponRepository.findByIdWithLock(couponId)).willReturn(Optional.of(expiredCoupon));
        given(userCouponRepository.existsByUserIdAndCouponIdWithLock(userId, couponId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponExpiredException.class)
                .hasMessageContaining(couponId);
    }

    @Test
    @DisplayName("재고가 없는 쿠폰 발급 실패")
    void 재고_없음() {
        // given
        String userId = "U001";
        String couponId = "C003";

        Coupon soldOutCoupon = Coupon.builder()
                .couponId(couponId)
                .name("품절된 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(0, 0)) // 재고 없음
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(couponRepository.findByIdWithLock(couponId)).willReturn(Optional.of(soldOutCoupon));
        given(userCouponRepository.existsByUserIdAndCouponIdWithLock(userId, couponId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponSoldOutException.class)
                .hasMessageContaining(couponId);
    }

    @Test
    @DisplayName("쿠폰 발급 시 재고가 1 감소")
    void 쿠폰_재고_감소_확인() {
        // given
        String userId = "U001";
        String couponId = "C001";

        Coupon coupon = Coupon.builder()
                .couponId(couponId)
                .name("5만원 할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(100, 50)) // 남은 재고 50
                .minOrderAmount(100000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build();

        given(couponRepository.findByIdWithLock(couponId)).willReturn(Optional.of(coupon));
        given(userCouponRepository.existsByUserIdAndCouponIdWithLock(userId, couponId)).willReturn(false);

        ArgumentCaptor<Coupon> couponCaptor = ArgumentCaptor.forClass(Coupon.class);

        // when
        issueCouponUseCase.execute(userId, couponId);

        // then
        verify(couponRepository).save(couponCaptor.capture());
        Coupon savedCoupon = couponCaptor.getValue();
        assertThat(savedCoupon.getStock().remainingQuantity()).isEqualTo(49); // 50 -> 49
    }
}
