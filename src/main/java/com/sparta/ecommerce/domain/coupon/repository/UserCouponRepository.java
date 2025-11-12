package com.sparta.ecommerce.domain.coupon.repository;

import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 사용자 쿠폰 저장소 인터페이스
 */
public interface UserCouponRepository extends JpaRepository<UserCoupon, String> {

    /**
     * 사용자 ID로 쿠폰 목록 조회
     */
    List<UserCoupon> findByUserId(String userId);

    /**
     * 사용자가 특정 쿠폰을 이미 발급받았는지 확인
     */
    boolean existsByUserIdAndCouponId(String userId, String couponId);

    /**
     * 사용자가 특정 쿠폰을 이미 발급받았는지 확인 (비관적 락)
     * SELECT FOR UPDATE로 동시성 제어
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END FROM UserCoupon uc WHERE uc.userId = :userId AND uc.couponId = :couponId")
    boolean existsByUserIdAndCouponIdWithLock(@Param("userId") String userId, @Param("couponId") String couponId);
}
