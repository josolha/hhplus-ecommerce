package com.sparta.ecommerce.presentation.controller.coupon;

import com.sparta.ecommerce.application.coupon.GetAvailableCouponsUseCase;
import com.sparta.ecommerce.application.coupon.IssueCouponUseCase;
import com.sparta.ecommerce.application.coupon.ValidateCouponUseCase;
import com.sparta.ecommerce.application.coupon.dto.CouponResponse;
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

    private final GetAvailableCouponsUseCase getAvailableCouponsUseCase;
    private final ValidateCouponUseCase validateCouponUseCase;
    private final IssueCouponUseCase issueCouponUseCase;

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
     * 쿠폰 발급 (선착순)
     * POST /api/coupons/{couponId}/issue
     */
    @Operation(summary = "쿠폰 발급", description = "선착순으로 쿠폰을 발급합니다")
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<UserCouponResponse> issueCoupon(
            @Parameter(description = "쿠폰 ID") @PathVariable String couponId,
            @Valid @RequestBody IssueCouponRequest request) {
        UserCouponResponse response = issueCouponUseCase.execute(request.userId(), couponId);
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
