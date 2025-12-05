package com.sparta.ecommerce.presentation.controller.coupon;

import com.sparta.ecommerce.application.coupon.usecase.CreateCouponUseCase;
import com.sparta.ecommerce.application.coupon.usecase.GetAvailableCouponsUseCase;
import com.sparta.ecommerce.application.coupon.usecase.IssueCouponUseCase;
import com.sparta.ecommerce.application.coupon.usecase.IssueCouponWithQueueUseCase;
import com.sparta.ecommerce.application.coupon.usecase.ValidateCouponUseCase;
import com.sparta.ecommerce.application.coupon.dto.CouponQueueResponse;
import com.sparta.ecommerce.application.coupon.dto.CouponResponse;
import com.sparta.ecommerce.application.coupon.dto.CreateCouponRequest;
import com.sparta.ecommerce.application.coupon.dto.IssueCouponRequest;
import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.application.coupon.dto.ValidateCouponRequest;
import com.sparta.ecommerce.application.coupon.dto.ValidateCouponResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 쿠폰 시스템 API
 */
@Tag(name = "쿠폰 시스템", description = "쿠폰 발급 및 검증 API")
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CreateCouponUseCase createCouponUseCase;
    private final GetAvailableCouponsUseCase getAvailableCouponsUseCase;
    private final ValidateCouponUseCase validateCouponUseCase;
    private final IssueCouponUseCase issueCouponUseCase;
    private final IssueCouponWithQueueUseCase issueCouponWithQueueUseCase;

    /**
     * 쿠폰 생성 (관리자)
     * POST /api/coupons
     */
    @Operation(summary = "쿠폰 생성", description = "새로운 쿠폰을 생성합니다 (관리자)")
    @PostMapping
    public ResponseEntity<CouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        CouponResponse response = createCouponUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰 목록 조회 (발급 가능한 쿠폰)
     * GET /api/coupons
     */
    @Operation(summary = "쿠폰 목록 조회", description = "발급 가능한 쿠폰 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<List<CouponResponse>> getCoupons() {
        List<CouponResponse> coupons = getAvailableCouponsUseCase.execute();
        return ResponseEntity.ok(coupons);
    }

    /**
     * 쿠폰 발급 (선착순 - 분산 락 방식)
     * POST /api/coupons/{couponId}/issue
     */
    @Operation(summary = "쿠폰 발급 (분산 락)", description = "분산 락을 사용하여 선착순으로 쿠폰을 발급합니다")
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<UserCouponResponse> issueCoupon(
            @Parameter(description = "쿠폰 ID") @PathVariable String couponId,
            @Valid @RequestBody IssueCouponRequest request) {
        UserCouponResponse response = issueCouponUseCase.execute(request.userId(), couponId);
        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰 발급 (선착순 - Redis 큐 방식)
     * POST /api/coupons/{couponId}/issue/queue
     */
    @Operation(summary = "쿠폰 발급 (큐)", description = "Redis 큐를 사용하여 선착순으로 쿠폰을 발급합니다 (비동기 처리)")
    @PostMapping("/{couponId}/issue/queue")
    public ResponseEntity<CouponQueueResponse> issueCouponWithQueue(
            @Parameter(description = "쿠폰 ID") @PathVariable String couponId,
            @Valid @RequestBody IssueCouponRequest request) {
        CouponQueueResponse response = issueCouponWithQueueUseCase.execute(request.userId(), couponId);
        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰 유효성 검증
     * POST /api/coupons/validate
     */
    @Operation(summary = "쿠폰 유효성 검증", description = "쿠폰 사용 가능 여부를 검증하고 할인 금액을 계산합니다")
    @PostMapping("/validate")
    public ResponseEntity<ValidateCouponResponse> validateCoupon(
            @Valid @RequestBody ValidateCouponRequest request) {
        ValidateCouponResponse response = validateCouponUseCase.execute(request);
        return ResponseEntity.ok(response);
    }
}
