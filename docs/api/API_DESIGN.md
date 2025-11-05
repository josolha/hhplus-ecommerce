# 이커머스 API 설계

## 1. 상품 관리 API

### 상품 목록 조회
#### **`GET /api/products`**

**Query:**
- `category`: 카테고리별 필터링 (optional)
- `sort`: 정렬 기준 - "price"(가격순), "popularity"(인기순), "newest"(최신순) (optional)

**Response:**
```
products: [
  {
    productId: string,        // 상품 ID
    name: string,             // 상품명
    price: number,            // 가격
    stock: number,            // 재고 수량
    category: string          // 카테고리
  }
]
```

### 상품 상세 조회
#### **`GET /api/products/{productId}`**

**Response:**
```
productId: string         // 상품 ID
name: string              // 상품명
price: number             // 가격
stock: number             // 재고 수량
category: string          // 카테고리
description: string       // 상품 상세 설명
```

### 재고 실시간 확인
#### **`GET /api/products/{productId}/stock`**

**Response:**
```
productId: string         // 상품 ID
stock: number             // 재고 수량
available: boolean        // 재고 있음 여부
```

### 인기 상품 조회 (최근 3일, Top 5)
#### **`GET /api/products/popular`**

**Query:**
- `days`: 조회 기간 (일 단위, default: 3)
- `limit`: 조회할 상품 개수 (default: 5)

**Response:**
```
period: {
  days: number,           // 조회된 일수
  startDate: string,      // 조회 시작일 (YYYY-MM-DD)
  endDate: string         // 조회 종료일 (YYYY-MM-DD)
}
products: [
  {
    productId: string,    // 상품 ID
    name: string,         // 상품명
    price: number,        // 가격
    stock: number,        // 재고 수량
    orderCount: number,   // 주문 수
    rank: number          // 순위
  }
]
```

---

## 2. 주문/결제 시스템 API

### 장바구니 조회
#### **`GET /api/cart`**

**Query:**
- `userId`: 조회할 사용자 ID

**Response:**
```
items: [
  {
    cartItemId: string,       // 장바구니 항목 ID
    productId: string,        // 상품 ID
    productName: string,      // 상품명
    price: number,            // 단가
    quantity: number,         // 수량
    subtotal: number          // 소계 (단가 × 수량)
  }
]
totalAmount: number           // 총 금액
```

### 장바구니 상품 추가
#### **`POST /api/cart/items`**

**Request:**
```
userId: string                // 사용자 ID
productId: string             // 상품 ID
quantity: number              // 수량
```

**Response:**
```
cartItemId: string            // 생성된 장바구니 항목 ID
productId: string             // 상품 ID
quantity: number              // 수량
message: string               // 응답 메시지
```

### 장바구니 상품 수량 변경
#### **`PATCH /api/cart/items/{cartItemId}`**

**Request:**
```
quantity: number              // 변경할 수량
```

**Response:**
```
cartItemId: string            // 장바구니 항목 ID
quantity: number              // 변경된 수량
message: string               // 응답 메시지
```

### 장바구니 상품 삭제
#### **`DELETE /api/cart/items/{cartItemId}`**

**Response:**
```
message: string               // 응답 메시지
```

### 주문 생성 (결제)
#### **`POST /api/orders`**

**Request:**
```
userId: string                // 사용자 ID
items: [
  {
    productId: string,        // 상품 ID
    quantity: number          // 수량
  }
]
couponId: string (optional)   // 쿠폰 ID
```

**Response:**
```
orderId: string               // 주문 ID
items: [
  {
    productId: string,        // 상품 ID
    productName: string,      // 상품명
    price: number,            // 단가
    quantity: number,         // 수량
    subtotal: number          // 소계
  }
]
totalAmount: number           // 총 금액
discountAmount: number        // 할인 금액
finalAmount: number           // 최종 결제 금액
remainingBalance: number      // 결제 후 잔액
createdAt: string             // 주문 생성 일시
```

**Error (재고 부족):**
```
error: string                 // 에러 코드
message: string               // 에러 메시지
insufficientItems: [
  {
    productId: string,        // 재고 부족 상품 ID
    requestedQuantity: number,// 요청 수량
    availableStock: number    // 현재 재고 수량
  }
]
```

**Error (잔액 부족):**
```
error: string                 // 에러 코드
message: string               // 에러 메시지
requiredAmount: number        // 필요 금액
currentBalance: number        // 현재 잔액
```

### 주문 목록 조회
#### **`GET /api/orders`**

**Query:**
- `userId`: 조회할 사용자 ID
- `page`: 페이지 번호 (default: 1)
- `limit`: 페이지당 조회 개수 (default: 10)
- `status`: 주문 상태 필터 - "completed"(완료), "cancelled"(취소) (optional)

**Response:**
```
orders: [
  {
    orderId: string,          // 주문 ID
    totalAmount: number,      // 총 금액
    discountAmount: number,   // 할인 금액
    finalAmount: number,      // 최종 결제 금액
    status: string,           // 주문 상태
    createdAt: string         // 주문 생성 일시
  }
]
pagination: {
  currentPage: number,        // 현재 페이지
  totalPages: number,         // 전체 페이지 수
  totalCount: number          // 전체 주문 개수
}
```

### 주문 상세 조회
#### **`GET /api/orders/{orderId}`**

**Response:**
```
orderId: string               // 주문 ID
items: [
  {
    productId: string,        // 상품 ID
    productName: string,      // 상품명
    price: number,            // 단가
    quantity: number,         // 수량
    subtotal: number          // 소계
  }
]
totalAmount: number           // 총 금액
discountAmount: number        // 할인 금액
finalAmount: number           // 최종 결제 금액
couponId: string (optional)   // 사용된 쿠폰 ID
status: string                // 주문 상태
createdAt: string             // 주문 생성 일시
```

---

## 3. 쿠폰 시스템 API

### 쿠폰 목록 조회 (발급 가능한 쿠폰)
#### **`GET /api/coupons`**

**Response:**
```
coupons: [
  {
    couponId: string,           // 쿠폰 ID
    name: string,               // 쿠폰명
    discountType: "percentage" | "fixed",  // 할인 타입 (퍼센트/정액)
    discountValue: number,      // 할인 값 (10% → 10, 5000원 → 5000)
    maxQuantity: number,        // 최대 발급 수량
    issuedQuantity: number,     // 발급된 수량
    remainingQuantity: number,  // 남은 수량
    expiresAt: string           // 만료 일시
  }
]
```

### 쿠폰 발급 (선착순)
#### **`POST /api/coupons/{couponId}/issue`**

**Request:**
```
userId: string                  // 사용자 ID
```

**Response:**
```
userCouponId: string            // 발급된 사용자 쿠폰 ID
couponId: string                // 쿠폰 ID
name: string                    // 쿠폰명
discountType: string            // 할인 타입
discountValue: number           // 할인 값
issuedAt: string                // 발급 일시
expiresAt: string               // 만료 일시
```

**Error (수량 소진):**
```
error: string                   // 에러 코드
message: string                 // 에러 메시지
```

**Error (이미 발급):**
```
error: string                   // 에러 코드
message: string                 // 에러 메시지
```

### 내 쿠폰 목록 조회
#### **`GET /api/users/{userId}/coupons`**

**Path:**
- `userId`: 조회할 사용자 ID

**Query:**
- `status`: 쿠폰 상태 필터 - "available"(사용가능), "used"(사용완료), "expired"(만료) (optional)

**Response:**
```
coupons: [
  {
    userCouponId: string,       // 사용자 쿠폰 ID
    couponId: string,           // 쿠폰 ID
    name: string,               // 쿠폰명
    discountType: "percentage" | "fixed",  // 할인 타입
    discountValue: number,      // 할인 값
    status: "available" | "used" | "expired",  // 상태
    issuedAt: string,           // 발급 일시
    usedAt: string (optional),  // 사용 일시
    expiresAt: string           // 만료 일시
  }
]
```

### 쿠폰 유효성 검증
#### **`POST /api/coupons/validate`**

**Request:**
```
userId: string                  // 사용자 ID
couponId: string                // 쿠폰 ID
orderAmount: number             // 주문 금액
```

**Response:**
```
valid: boolean                  // 유효 여부
discountAmount: number          // 할인 금액
finalAmount: number             // 할인 후 최종 금액
message: string                 // 응답 메시지
```

---

## 4. 사용자 API

### 잔액 조회
#### **`GET /api/users/{userId}/balance`**

**Path:**
- `userId`: 조회할 사용자 ID

**Response:**
```
userId: string                  // 사용자 ID
balance: number                 // 현재 잔액
```

### 잔액 충전
#### **`POST /api/users/{userId}/balance/charge`**

**Path:**
- `userId`: 충전할 사용자 ID

**Request:**
```
amount: number                  // 충전 금액
```

**Response:**
```
userId: string                  // 사용자 ID
previousBalance: number         // 충전 전 잔액
chargedAmount: number           // 충전 금액
currentBalance: number          // 충전 후 잔액
chargedAt: string               // 충전 일시
```

---

## 5. 외부 연동 API (내부 시스템용)

### 주문 데이터 전송 상태 조회
#### **`GET /api/admin/orders/{orderId}/sync-status`**

**Response:**
```
orderId: string                 // 주문 ID
synced: boolean                 // 전송 완료 여부
syncedAt: string (optional)     // 전송 완료 일시
syncAttempts: number            // 전송 시도 횟수
lastSyncError: string (optional)// 마지막 전송 실패 에러 메시지
```

### 주문 데이터 재전송
#### **`POST /api/admin/orders/{orderId}/resync`**

**Response:**
```
orderId: string                 // 주문 ID
synced: boolean                 // 전송 완료 여부
syncedAt: string                // 전송 완료 일시
message: string                 // 응답 메시지
```

---

## 공통 Error Response

```
HTTP Status: 400 | 401 | 404 | 500
Response:
  error: string               // 에러 코드
  message: string             // 에러 메시지
  details: object (optional)  // 에러 상세 정보
```

---

## 에러 코드 정의

### JavaScript/TypeScript
```javascript
const ErrorCodes = {
  // 상품 관련
  PRODUCT_NOT_FOUND: 'P001',
  INSUFFICIENT_STOCK: 'P002',

  // 주문 관련
  INVALID_QUANTITY: 'O001',
  ORDER_NOT_FOUND: 'O002',

  // 결제 관련
  INSUFFICIENT_BALANCE: 'PAY001',
  PAYMENT_FAILED: 'PAY002',

  // 쿠폰 관련
  COUPON_SOLD_OUT: 'C001',
  INVALID_COUPON: 'C002',
  EXPIRED_COUPON: 'C003',
  ALREADY_USED: 'C004',

  // 공통
  INVALID_REQUEST: 'COMMON001',
  UNAUTHORIZED: 'COMMON002',
  FORBIDDEN: 'COMMON003',
  INTERNAL_ERROR: 'COMMON004'
}
```

### Java
```java
public class ErrorCodes {

    // 상품 관련
    public static final String PRODUCT_NOT_FOUND = "P001";
    public static final String INSUFFICIENT_STOCK = "P002";

    // 주문 관련
    public static final String INVALID_QUANTITY = "O001";
    public static final String ORDER_NOT_FOUND = "O002";

    // 결제 관련
    public static final String INSUFFICIENT_BALANCE = "PAY001";
    public static final String PAYMENT_FAILED = "PAY002";

    // 쿠폰 관련
    public static final String COUPON_SOLD_OUT = "C001";
    public static final String INVALID_COUPON = "C002";
    public static final String EXPIRED_COUPON = "C003";
    public static final String ALREADY_USED = "C004";

    // 공통
    public static final String INVALID_REQUEST = "COMMON001";
    public static final String UNAUTHORIZED = "COMMON002";
    public static final String FORBIDDEN = "COMMON003";
    public static final String INTERNAL_ERROR = "COMMON004";
}
```

