package com.sparta.ecommerce.application.coupon.usecase;

import com.sparta.ecommerce.application.coupon.dto.CouponResponse;
import com.sparta.ecommerce.application.coupon.dto.CreateCouponRequest;
import com.sparta.ecommerce.application.coupon.worker.CouponWorker;
import com.sparta.ecommerce.domain.coupon.entity.Coupon;
import com.sparta.ecommerce.domain.coupon.repository.CouponRepository;
import com.sparta.ecommerce.domain.coupon.vo.CouponStock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 생성 유스케이스 (관리자용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateCouponUseCase {

    private final CouponRepository couponRepository;
    private final CouponWorker couponWorker;

    @Transactional
    public CouponResponse execute(CreateCouponRequest request) {
        // 1. CouponStock 생성
        CouponStock stock = new CouponStock(
                request.totalQuantity(),
                0,  // 발급된 수량
                request.totalQuantity()  // 남은 수량
        );

        // 2. Coupon 엔티티 생성 (UUID 자동 생성)
        Coupon coupon = Coupon.builder()
                .name(request.name())
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .stock(stock)
                .minOrderAmount(request.minOrderAmount())
                .expiresAt(request.expiresAt())
                .build();

        // 3. 저장 (UUID 자동 생성됨)
        Coupon savedCoupon = couponRepository.save(coupon);

        log.info("쿠폰 생성 완료: couponId={}, name={}, quantity={}",
                savedCoupon.getCouponId(), savedCoupon.getName(), request.totalQuantity());

        // 4. 새로 생성된 쿠폰에 대한 Worker 시작
        couponWorker.startWorkerForCoupon(savedCoupon.getCouponId());
        log.info("쿠폰 Worker 시작: couponId={}", savedCoupon.getCouponId());

        // 5. 응답 변환
        return CouponResponse.from(savedCoupon);
    }
}
