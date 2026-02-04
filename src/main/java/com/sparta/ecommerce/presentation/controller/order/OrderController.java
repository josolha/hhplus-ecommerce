package com.sparta.ecommerce.presentation.controller.order;

import com.sparta.ecommerce.application.order.usecase.CreateOrderUseCase;
import com.sparta.ecommerce.application.order.usecase.GetOrderDetailUseCase;
import com.sparta.ecommerce.application.order.usecase.GetOrdersUseCase;
import com.sparta.ecommerce.application.order.dto.CreateOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 주문 관리 API
 */
@Tag(name = "주문 관리", description = "주문 생성 및 조회 API")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrdersUseCase getOrdersUseCase;
    private final GetOrderDetailUseCase getOrderDetailUseCase;

    /**
     * 주문 생성 (결제)
     * POST /api/orders
     */
    @Operation(
            summary = "주문 생성",
            description = "장바구니 상품을 주문하고 결제를 진행합니다. 재고 차감 및 잔액 결제가 트랜잭션으로 처리됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "주문 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "orderId": "ORD001",
                                      "items": [
                                        {
                                          "productId": "P001",
                                          "productName": "상품명",
                                          "price": 10000,
                                          "quantity": 2,
                                          "subtotal": 20000
                                        }
                                      ],
                                      "totalAmount": 20000,
                                      "discountAmount": 2000,
                                      "finalAmount": 18000,
                                      "createdAt": "2025-02-04T10:30:00"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "재고 부족 또는 잔액 부족",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "재고 부족", value = """
                                            {
                                              "error": "P002",
                                              "message": "재고가 부족합니다",
                                              "insufficientItems": [
                                                {
                                                  "productId": "P001",
                                                  "requestedQuantity": 10,
                                                  "availableStock": 5
                                                }
                                              ]
                                            }
                                            """),
                                    @ExampleObject(name = "잔액 부족", value = """
                                            {
                                              "error": "PAY001",
                                              "message": "잔액이 부족합니다",
                                              "requiredAmount": 18000,
                                              "currentBalance": 10000
                                            }
                                            """)
                            }
                    )
            )
    })
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest orderRequest) {
        var response = createOrderUseCase.execute(orderRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * 주문 목록 조회
     * GET /api/orders
     */
    @Operation(summary = "주문 목록 조회", description = "사용자의 주문 목록을 페이징하여 조회합니다")
    @GetMapping
    public ResponseEntity<?> getOrders(
            @Parameter(description = "사용자 ID") @RequestParam String userId,
            @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지당 조회 개수") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "주문 상태 (completed/cancelled)") @RequestParam(required = false) String status) {

        var response = getOrdersUseCase.execute(userId, page, limit, status);
        return ResponseEntity.ok(response);
    }

    /**
     * 주문 상세 조회
     * GET /api/orders/{orderId}
     */
    @Operation(summary = "주문 상세 조회", description = "특정 주문의 상세 정보를 조회합니다")
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderDetail(
            @Parameter(description = "주문 ID") @PathVariable String orderId) {

        var response = getOrderDetailUseCase.execute(orderId);
        return ResponseEntity.ok(response);
    }
}
