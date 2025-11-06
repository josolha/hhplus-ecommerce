package com.sparta.ecommerce.domain.coupon.vo;

import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CouponStock VO 테스트
 */
@DisplayName("CouponStock VO 테스트")
class CouponStockTest {

    @Test
    @DisplayName("유효한 재고로 CouponStock을 생성한다")
    void 유효한_재고_생성() {
        // given & when
        CouponStock stock = new CouponStock(100, 50);

        // then
        assertThat(stock.totalQuantity()).isEqualTo(100);
        assertThat(stock.remainingQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("총 수량이 음수면 예외가 발생한다")
    void 총수량_음수_검증() {
        // when & then
        assertThatThrownBy(() -> new CouponStock(-1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("총 수량은 음수일 수 없습니다");
    }

    @Test
    @DisplayName("남은 수량이 음수면 예외가 발생한다")
    void 남은수량_음수_검증() {
        // when & then
        assertThatThrownBy(() -> new CouponStock(100, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("남은 수량은 음수일 수 없습니다");
    }

    @Test
    @DisplayName("남은 수량이 총 수량보다 크면 예외가 발생한다")
    void 남은수량_총수량_초과_검증() {
        // when & then
        assertThatThrownBy(() -> new CouponStock(100, 150))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("남은 수량은 총 수량보다 클 수 없습니다");
    }

    @Test
    @DisplayName("쿠폰을 발급하면 남은 수량이 1 감소한다")
    void 쿠폰_발급_성공() {
        // given
        CouponStock stock = new CouponStock(100, 50);

        // when
        CouponStock issuedStock = stock.issue();

        // then
        assertThat(issuedStock.totalQuantity()).isEqualTo(100);
        assertThat(issuedStock.remainingQuantity()).isEqualTo(49);
        assertThat(stock.remainingQuantity()).isEqualTo(50); // 불변성 확인
    }

    @Test
    @DisplayName("재고가 0이면 발급 시 예외가 발생한다")
    void 재고_소진_시_발급_실패() {
        // given
        CouponStock stock = new CouponStock(100, 0);

        // when & then
        assertThatThrownBy(stock::issue)
                .isInstanceOf(CouponSoldOutException.class)
                .hasMessage("쿠폰이 모두 소진되었습니다");
    }

    @Test
    @DisplayName("재고가 있으면 hasStock이 true를 반환한다")
    void 재고_있음() {
        // given
        CouponStock stock = new CouponStock(100, 50);

        // when & then
        assertThat(stock.hasStock()).isTrue();
    }

    @Test
    @DisplayName("재고가 0이면 hasStock이 false를 반환한다")
    void 재고_없음() {
        // given
        CouponStock stock = new CouponStock(100, 0);

        // when & then
        assertThat(stock.hasStock()).isFalse();
    }

    @Test
    @DisplayName("재고가 0이면 isOutOfStock이 true를 반환한다")
    void 재고_소진_확인() {
        // given
        CouponStock stock = new CouponStock(100, 0);

        // when & then
        assertThat(stock.isOutOfStock()).isTrue();
    }

    @Test
    @DisplayName("재고가 있으면 isOutOfStock이 false를 반환한다")
    void 재고_남음_확인() {
        // given
        CouponStock stock = new CouponStock(100, 50);

        // when & then
        assertThat(stock.isOutOfStock()).isFalse();
    }

    @Test
    @DisplayName("발급률을 정확하게 계산한다")
    void 발급률_계산() {
        // given
        CouponStock stock = new CouponStock(100, 50);

        // when
        double issuanceRate = stock.getIssuanceRate();

        // then
        assertThat(issuanceRate).isEqualTo(50.0); // 50개 발급 / 100개 = 50%
    }

    @Test
    @DisplayName("모두 발급되면 발급률이 100%이다")
    void 발급률_100퍼센트() {
        // given
        CouponStock stock = new CouponStock(100, 0);

        // when
        double issuanceRate = stock.getIssuanceRate();

        // then
        assertThat(issuanceRate).isEqualTo(100.0);
    }

    @Test
    @DisplayName("하나도 발급되지 않으면 발급률이 0%이다")
    void 발급률_0퍼센트() {
        // given
        CouponStock stock = new CouponStock(100, 100);

        // when
        double issuanceRate = stock.getIssuanceRate();

        // then
        assertThat(issuanceRate).isEqualTo(0.0);
    }

    @Test
    @DisplayName("총 수량이 0이면 발급률이 0%이다")
    void 총수량_0일때_발급률() {
        // given
        CouponStock stock = new CouponStock(0, 0);

        // when
        double issuanceRate = stock.getIssuanceRate();

        // then
        assertThat(issuanceRate).isEqualTo(0.0);
    }

    @Test
    @DisplayName("발급된 수량을 정확하게 계산한다")
    void 발급된_수량_계산() {
        // given
        CouponStock stock = new CouponStock(100, 30);

        // when
        int issuedQuantity = stock.getIssuedQuantity();

        // then
        assertThat(issuedQuantity).isEqualTo(70); // 100 - 30 = 70
    }

    @Test
    @DisplayName("연속으로 발급하면 수량이 순차적으로 감소한다")
    void 연속_발급() {
        // given
        CouponStock stock = new CouponStock(100, 3);

        // when
        CouponStock stock1 = stock.issue();
        CouponStock stock2 = stock1.issue();
        CouponStock stock3 = stock2.issue();

        // then
        assertThat(stock1.remainingQuantity()).isEqualTo(2);
        assertThat(stock2.remainingQuantity()).isEqualTo(1);
        assertThat(stock3.remainingQuantity()).isEqualTo(0);
        assertThat(stock3.isOutOfStock()).isTrue();

        // 다음 발급은 실패
        assertThatThrownBy(stock3::issue)
                .isInstanceOf(CouponSoldOutException.class);
    }
}
