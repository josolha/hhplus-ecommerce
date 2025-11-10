package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.coupon.UserCoupon;
import com.sparta.ecommerce.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 인메모리 사용자 쿠폰 저장소
 *
 * Concurrency Control:
 * - ReadWriteLock으로 읽기/쓰기 분리
 * - 조회는 동시 실행, 발급/수정은 배타적 실행
 */
@Repository
@RequiredArgsConstructor
public class InMemoryUserCouponRepository implements UserCouponRepository {

    private final InMemoryDataStore dataStore;

    /**
     * ReadWriteLock: 사용자 쿠폰 동시성 제어
     */
    private final ReadWriteLock userCouponLock = new ReentrantReadWriteLock();

    @Override
    public Optional<UserCoupon> findById(String userCouponId) {
        // 읽기 락: 조회는 동시 실행 가능
        userCouponLock.readLock().lock();
        try {
            return Optional.ofNullable(dataStore.getUserCoupons().get(userCouponId));
        } finally {
            userCouponLock.readLock().unlock();
        }
    }

    @Override
    public List<UserCoupon> findByUserId(String userId) {
        // 읽기 락: 사용자별 쿠폰 목록 조회는 동시 실행 가능
        userCouponLock.readLock().lock();
        try {
            return dataStore.getUserCoupons().values().stream()
                    .filter(userCoupon -> userId.equals(userCoupon.getUserId()))
                    .toList();
        } finally {
            userCouponLock.readLock().unlock();
        }
    }

    @Override
    public boolean existsByUserIdAndCouponId(String userId, String couponId) {
        // 읽기 락: 일반 조회는 동시 실행 가능
        userCouponLock.readLock().lock();
        try {
            return dataStore.getUserCoupons().values().stream()
                    .anyMatch(userCoupon ->
                            userId.equals(userCoupon.getUserId()) &&
                            couponId.equals(userCoupon.getCouponId())
                    );
        } finally {
            userCouponLock.readLock().unlock();
        }
    }

    @Override
    public boolean existsByUserIdAndCouponIdWithLock(String userId, String couponId) {
        // 쓰기 락: 중복 발급 체크는 비관적 락으로 보호
        // 실제 DB 환경: SELECT * FROM user_coupon WHERE user_id = ? AND coupon_id = ? FOR UPDATE
        userCouponLock.writeLock().lock();
        try {
            return dataStore.getUserCoupons().values().stream()
                    .anyMatch(userCoupon ->
                            userId.equals(userCoupon.getUserId()) &&
                            couponId.equals(userCoupon.getCouponId())
                    );
        } finally {
            userCouponLock.writeLock().unlock();
        }
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        // 쓰기 락: 저장은 배타적 실행
        userCouponLock.writeLock().lock();
        try {
            dataStore.getUserCoupons().put(userCoupon.getUserCouponId(), userCoupon);
            return userCoupon;
        } finally {
            userCouponLock.writeLock().unlock();
        }
    }
}
