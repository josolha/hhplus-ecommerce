package com.sparta.ecommerce.domain.coupon.repository;

import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 쿠폰 발급 (재고 차감) - 원자적 UPDATE 쿼리
     * 재고가 있을 때만 차감하여 음수 재고 방지
     *
     * @param couponId 쿠폰 ID
     * @return 업데이트된 행 수 (0이면 재고 부족, 1이면 성공)
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.stock.issuedQuantity = c.stock.issuedQuantity + 1, " +
           "c.stock.remainingQuantity = c.stock.remainingQuantity - 1 " +
           "WHERE c.couponId = :couponId AND c.stock.remainingQuantity > 0")
    int issueCoupon(@Param("couponId") String couponId);
}
