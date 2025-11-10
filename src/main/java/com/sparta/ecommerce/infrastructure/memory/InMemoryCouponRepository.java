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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 인메모리 쿠폰 저장소
 *
 * Concurrency Control:
 * - ReadWriteLock 사용으로 읽기 성능 최적화
 * - 읽기(Read Lock): 여러 스레드 동시 접근 가능
 * - 쓰기(Write Lock): 단독 접근만 허용
 * - 실제 DB 환경에서는 SELECT FOR UPDATE로 대체됨
 */
@Repository
@RequiredArgsConstructor
public class InMemoryCouponRepository implements CouponRepository {

    private final InMemoryDataStore dataStore;

    /**
     * ReadWriteLock: 읽기/쓰기 분리로 동시성 향상
     * - 읽기 락: 공유 가능 (여러 스레드가 동시에 읽기 가능)
     * - 쓰기 락: 배타적 (쓰기 중에는 읽기/쓰기 모두 차단)
     */
    private final ReadWriteLock couponLock = new ReentrantReadWriteLock();

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
        // 읽기 락 사용: 여러 스레드가 동시에 조회 가능
        couponLock.readLock().lock();
        try {
            return Optional.ofNullable(dataStore.getCoupons().get(couponId));
        } finally {
            couponLock.readLock().unlock();
        }
    }

    @Override
    public Optional<Coupon> findByIdWithLock(String couponId) {
        // 쓰기 락 사용: 비관적 락 시뮬레이션
        // 실제 DB 환경: SELECT * FROM coupon WHERE id = ? FOR UPDATE
        // 쓰기 락이므로 다른 모든 스레드(읽기/쓰기)를 차단
        couponLock.writeLock().lock();
        try {
            return Optional.ofNullable(dataStore.getCoupons().get(couponId));
        } finally {
            couponLock.writeLock().unlock();
        }
    }

    @Override
    public List<Coupon> findAll() {
        // 읽기 락: 전체 조회는 여러 스레드가 동시 실행 가능
        couponLock.readLock().lock();
        try {
            return new ArrayList<>(dataStore.getCoupons().values());
        } finally {
            couponLock.readLock().unlock();
        }
    }

    @Override
    public List<Coupon> findAvailableCoupons() {
        // 읽기 락: 필터링 조회도 여러 스레드가 동시 실행 가능
        couponLock.readLock().lock();
        try {
            return dataStore.getCoupons().values().stream()
                    .filter(Coupon::isAvailable)
                    .toList();
        } finally {
            couponLock.readLock().unlock();
        }
    }

    @Override
    public void save(Coupon coupon) {
        // 쓰기 락: 저장은 배타적으로 실행 (다른 읽기/쓰기 차단)
        couponLock.writeLock().lock();
        try {
            dataStore.getCoupons().put(coupon.getCouponId(), coupon);
        } finally {
            couponLock.writeLock().unlock();
        }
    }
}
