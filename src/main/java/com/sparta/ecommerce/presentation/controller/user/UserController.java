package com.sparta.ecommerce.presentation.controller.user;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.application.user.usecase.ChargeUserBalanceUseCase;
import com.sparta.ecommerce.application.user.usecase.GetUserBalanceUseCase;
import com.sparta.ecommerce.application.user.usecase.GetUserCouponsUseCase;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.application.user.dto.UserBalanceResponse;
import com.sparta.ecommerce.domain.coupon.CouponStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자 API
 */
@Tag(name = "사용자 관리", description = "사용자 잔액 및 쿠폰 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final GetUserBalanceUseCase getUserBalanceUseCase;
    private final ChargeUserBalanceUseCase chargeUserBalanceUseCase;
    private final GetUserCouponsUseCase getUserCouponsUseCase;

    /**
     * 잔액 조회
     * GET /api/users/{userId}/balance
     */
    @Operation(summary = "잔액 조회", description = "사용자의 현재 잔액을 조회합니다")
    @GetMapping("/{userId}/balance")
    public ResponseEntity<?> getBalance(
            @Parameter(description = "사용자 ID")
            @PathVariable String userId) {

        UserBalanceResponse userBalanceResponse = getUserBalanceUseCase.execute(userId);
        return ResponseEntity.ok(userBalanceResponse);
    }

    /**
     * 잔액 충전
     * POST /api/users/{userId}/balance/charge
     */
    @Operation(summary = "잔액 충전", description = "사용자의 잔액을 충전합니다")
    @PostMapping("/{userId}/balance/charge")
    public ResponseEntity<ChargeBalanceResponse> chargeBalance(
            @Parameter(description = "사용자 ID")
            @PathVariable String userId,
            @Valid @RequestBody ChargeBalanceRequest request) {

        ChargeBalanceResponse response = chargeUserBalanceUseCase.execute(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 내 쿠폰 목록 조회
     * GET /api/users/{userId}/coupons
     */
    @Operation(summary = "내 쿠폰 목록 조회", description = "사용자가 보유한 쿠폰 목록을 조회합니다")
    @GetMapping("/{userId}/coupons")
    public ResponseEntity<List<UserCouponResponse>> getUserCoupons(
            @Parameter(description = "사용자 ID")
            @PathVariable String userId,
            @Parameter(description = "쿠폰 상태 (AVAILABLE/USED/EXPIRED)")
            @RequestParam(required = false)
            CouponStatus status) {

        List<UserCouponResponse> coupons = getUserCouponsUseCase.execute(userId, status);
        return ResponseEntity.ok(coupons);
    }
}
