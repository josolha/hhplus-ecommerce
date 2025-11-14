package com.sparta.ecommerce.domain.coupon.repository;

import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 저장소 인터페이스
 */
public interface CouponRepository extends JpaRepository<Coupon, String> {

    /**
     * ID로 쿠폰 조회 (비관적 락)
     * SELECT FOR UPDATE로 동시성 제어
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.couponId = :couponId")
    Optional<Coupon> findByIdWithLock(@Param("couponId") String couponId);

    /**
     * 발급 가능한 쿠폰 조회 (재고 있고 만료되지 않은 쿠폰)
     */
    @Query("SELECT c FROM Coupon c WHERE c.stock.remainingQuantity > 0 AND c.expiresAt > :now")
    List<Coupon> findAvailableCoupons(@Param("now") LocalDateTime now);
}
