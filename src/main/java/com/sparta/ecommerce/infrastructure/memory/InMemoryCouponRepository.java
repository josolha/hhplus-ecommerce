package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.coupon.Coupon;
import com.sparta.ecommerce.domain.coupon.CouponRepository;
import com.sparta.ecommerce.domain.coupon.DiscountType;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 인메모리 쿠폰 저장소
 */
@Repository
@RequiredArgsConstructor
public class InMemoryCouponRepository implements CouponRepository {

    private final InMemoryDataStore dataStore;

    @PostConstruct
    public void init() {
        // 초기 쿠폰 데이터 생성
        save(Coupon.builder()
                .couponId("C001")
                .name("신규 가입 5만원 할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(50000)
                .stock(new CouponStock(100, 50))
                .minOrderAmount(100000)
                .expiresAt(LocalDateTime.now().plusMonths(2))
                .createdAt(LocalDateTime.now())
                .build());

        save(Coupon.builder()
                .couponId("C002")
                .name("10% 할인 쿠폰")
                .discountType(DiscountType.PERCENT)
                .discountValue(10)
                .stock(new CouponStock(200, 150))
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build());

        save(Coupon.builder()
                .couponId("C003")
                .name("VIP 20만원 할인 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(200000)
                .stock(new CouponStock(50, 10))
                .minOrderAmount(1000000)
                .expiresAt(LocalDateTime.now().plusMonths(3))
                .createdAt(LocalDateTime.now())
                .build());

        save(Coupon.builder()
                .couponId("C004")
                .name("품절된 쿠폰")
                .discountType(DiscountType.FIXED)
                .discountValue(30000)
                .stock(new CouponStock(100, 0))  // 품절
                .minOrderAmount(50000)
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .createdAt(LocalDateTime.now())
                .build());

        save(Coupon.builder()
                .couponId("C005")
                .name("만료된 쿠폰")
                .discountType(DiscountType.PERCENT)
                .discountValue(15)
                .stock(new CouponStock(100, 50))
                .minOrderAmount(30000)
                .expiresAt(LocalDateTime.now().minusDays(1))  // 만료됨
                .createdAt(LocalDateTime.now().minusMonths(2))
                .build());
    }

    @Override
    public Optional<Coupon> findById(String couponId) {
        return Optional.ofNullable(dataStore.getCoupons().get(couponId));
    }

    @Override
    public List<Coupon> findAll() {
        return new ArrayList<>(dataStore.getCoupons().values());
    }

    @Override
    public List<Coupon> findAvailableCoupons() {
        return dataStore.getCoupons().values().stream()
                .filter(Coupon::isAvailable)
                .toList();
    }

    @Override
    public void save(Coupon coupon) {
        dataStore.getCoupons().put(coupon.getCouponId(), coupon);
    }
}
