package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.entity.UserCoupon;
import com.sparta.ecommerce.domain.coupon.repository.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 쿠폰 발급 유스케이스
 *
 * 동시성 제어 전략:
 * - @Transactional + 비관적 락 (SELECT FOR UPDATE)
 * - findByIdWithLock: 쿠폰 조회 시 행 레벨 락 획득
 * - existsByUserIdAndCouponIdWithLock: 중복 발급 체크 시 락 획득
 * - 트랜잭션 커밋 시 락 자동 해제
 * - 데이터베이스 레벨에서 동시성 제어 (분산 환경 대응 가능)
 */
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰 발급
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰 정보
     *
     * 동시성 제어 흐름:
     * 1. 트랜잭션 시작
     * 2. SELECT FOR UPDATE로 쿠폰 조회 (행 락 획득)
     * 3. 중복 발급 체크 (락 보호)
     * 4. 유효성 검증 (만료, 재고)
     * 5. 재고 차감 및 사용자 쿠폰 발급
     * 6. 트랜잭션 커밋 (락 자동 해제)
     *
     * @throws InvalidCouponException 쿠폰이 존재하지 않을 때
     * @throws DuplicateCouponIssueException 이미 발급받은 쿠폰일 때
     * @throws CouponExpiredException 만료된 쿠폰일 때
     * @throws CouponSoldOutException 재고가 없을 때
     */
    @Transactional
    public UserCouponResponse execute(String userId, String couponId) {
        // 1. 쿠폰 조회 (비관적 락)
        // SQL: SELECT * FROM coupons WHERE id = ? FOR UPDATE
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰입니다"));

        // 2. 중복 발급 체크 (비관적 락)
        // SQL: SELECT COUNT(*) FROM user_coupons WHERE user_id = ? AND coupon_id = ? FOR UPDATE
        if (userCouponRepository.existsByUserIdAndCouponIdWithLock(userId, couponId)) {
            throw new DuplicateCouponIssueException(couponId);
        }

        // 3. 만료 확인
        if (coupon.isExpired()) {
            throw new CouponExpiredException(couponId);
        }

        // 4. 재고 확인
        if (!coupon.hasStock()) {
            throw new CouponSoldOutException(couponId);
        }

        // 5. 쿠폰 재고 차감
        Coupon issuedCoupon = coupon.issue();
        couponRepository.save(issuedCoupon);

        // 6. 사용자 쿠폰 발급 이력 저장
        String userCouponId = "UC" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UserCoupon userCoupon = UserCoupon.issue(userCouponId, userId, issuedCoupon);
        userCouponRepository.save(userCoupon);

        // 7. 응답 생성
        return UserCouponResponse.from(userCoupon, issuedCoupon);
    }
}
