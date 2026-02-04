# 예외 처리 아키텍처

E-commerce 프로젝트의 전체 예외 처리 구조와 흐름을 설명합니다.

## 📋 목차
1. [예외 처리 구조 개요](#예외-처리-구조-개요)
2. [예외 클래스 계층](#예외-클래스-계층)
3. [예외 처리 흐름](#예외-처리-흐름)
4. [에러 코드 체계](#에러-코드-체계)
5. [레이어별 예외 처리](#레이어별-예외-처리)

---

## 예외 처리 구조 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Request                           │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                ┌───────────────▼────────────────┐
                │   JWT Authentication Filter    │ (Filter 레벨)
                │  - 토큰 검증                    │
                │  - 인증 실패 시 EntryPoint 호출  │
                └───────────────┬────────────────┘
                                │
                ┌───────────────▼────────────────┐
                │         Controller             │ (Presentation 레벨)
                │  - 요청 매핑                    │
                │  - Validation 검증              │
                └───────────────┬────────────────┘
                                │
                ┌───────────────▼────────────────┐
                │          UseCase               │ (Application 레벨)
                │  - 비즈니스 로직 실행            │
                │  - BusinessException 발생       │
                └───────────────┬────────────────┘
                                │
                ┌───────────────▼────────────────┐
                │      Domain / Service          │ (Domain 레벨)
                │  - 도메인 규칙 검증              │
                │  - 도메인 예외 발생              │
                └───────────────┬────────────────┘
                                │
                        ┌───────▼───────┐
                        │   Exception   │
                        └───────┬───────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
┌───────▼──────────┐   ┌────────▼────────┐   ┌────────▼────────┐
│ JWT Entry Point  │   │ Access Denied   │   │ Global Handler  │
│ (401 처리)        │   │ Handler         │   │ (400/409/500)   │
│ - AUTH001/002    │   │ (403 처리)       │   │ - Business      │
└──────────────────┘   └─────────────────┘   │ - Validation    │
                                             └─────────────────┘
                                                     │
                                             ┌───────▼───────┐
                                             │ Error Response│
                                             │ {"code": ...  │
                                             │  "message": }│
                                             └───────────────┘
```

---

## 예외 클래스 계층

### 1. 최상위 예외 (BusinessException)

```java
// common/exception/BusinessException.java
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
```

### 2. 도메인별 예외 계층

```
BusinessException (추상 클래스)
│
├── 🔐 인증 예외 (infrastructure/config/security/exception/)
│   ├── InvalidTokenException (AUTH001)
│   ├── ExpiredTokenException (AUTH002)
│   ├── LoggedOutTokenException (AUTH003)
│   └── TokenMismatchException (AUTH004)
│
├── 📦 상품 예외 (domain/product/exception/)
│   ├── ProductNotFoundException (P001)
│   └── InsufficientStockException (P002)
│
├── 🛒 주문 예외 (domain/order/exception/)
│   ├── InvalidOrderQuantityException (O001)
│   └── OrderNotFoundException (O002)
│
├── 💳 결제 예외 (domain/payment/exception/)
│   ├── InsufficientBalanceException (PAY001)
│   └── PaymentFailedException (PAY002)
│
├── 🎟️ 쿠폰 예외 (domain/coupon/exception/)
│   ├── CouponSoldOutException (C001)
│   ├── InvalidCouponException (C002)
│   ├── CouponExpiredException (C003)
│   ├── CouponAlreadyUsedException (C004)
│   ├── CouponIssueLockException (C005)
│   └── DuplicateCouponIssueException (C006)
│
└── 👤 사용자 예외 (domain/user/exception/)
    ├── UserNotFoundException
    └── InsufficientBalanceException
```

---

## 예외 처리 흐름

### 🔹 Flow 1: JWT Filter 레벨 예외 (401 Unauthorized)

```
Client Request (with Invalid Token)
         │
         ▼
┌────────────────────────┐
│ JwtAuthenticationFilter│
│  - validate() 실패      │
│  - ExpiredJwtException │
└────────┬───────────────┘
         │
         ▼
┌─────────────────────────────┐
│ JwtAuthenticationEntryPoint │
│  - EXPIRED_TOKEN 감지        │
│  - AUTH002로 매핑            │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  HTTP 401 Response          │
│  {                          │
│    "code": "AUTH002",       │
│    "message": "만료된 토큰"  │
│  }                          │
└─────────────────────────────┘
```

**코드 위치:**
- Filter: `JwtAuthenticationFilter.java:58-63`
- Entry Point: `JwtAuthenticationEntryPoint.java:47-60`

---

### 🔹 Flow 2: UseCase 레벨 예외 (401 Unauthorized)

```
Client Request (POST /api/auth/refresh)
         │
         ▼
┌──────────────────────────┐
│ AuthController           │
│  - refreshToken()        │
└────────┬─────────────────┘
         │
         ▼
┌────────────────────────────┐
│ RefreshAccessTokenUseCase  │
│  - validate() 실패          │
│  - throw InvalidToken...   │
└────────┬───────────────────┘
         │
         ▼
┌─────────────────────────────┐
│ GlobalExceptionHandler      │
│  - @ExceptionHandler        │
│  - code.startsWith("AUTH")  │
│  - HTTP 401 반환             │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  HTTP 401 Response          │
│  {                          │
│    "code": "AUTH001",       │
│    "message": "유효하지..."  │
│  }                          │
└─────────────────────────────┘
```

**코드 위치:**
- UseCase: `RefreshAccessTokenUseCase.java:31-32`
- Handler: `GlobalExceptionHandler.java:45-63`

---

### 🔹 Flow 3: 비즈니스 예외 (400 Bad Request)

```
Client Request (POST /api/coupons/issue)
         │
         ▼
┌──────────────────────────┐
│ CouponController         │
│  - issueCoupon()         │
└────────┬─────────────────┘
         │
         ▼
┌────────────────────────────┐
│ IssueCouponUseCase         │
│  - 쿠폰 품절 확인            │
│  - throw CouponSoldOut...  │
└────────┬───────────────────┘
         │
         ▼
┌─────────────────────────────┐
│ GlobalExceptionHandler      │
│  - @ExceptionHandler        │
│  - BusinessException        │
│  - HTTP 400 반환             │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  HTTP 400 Response          │
│  {                          │
│    "code": "C001",          │
│    "message": "쿠폰 소진"    │
│  }                          │
└─────────────────────────────┘
```

**코드 위치:**
- UseCase: `IssueCouponUseCase.java`
- Handler: `GlobalExceptionHandler.java:45-63`

---

### 🔹 Flow 4: 중복 발급 예외 (409 Conflict)

```
Client Request (POST /api/coupons/issue)
         │
         ▼
┌──────────────────────────────┐
│ IssueCouponUseCase           │
│  - 중복 체크 실패              │
│  - throw Duplicate...        │
└────────┬─────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ GlobalExceptionHandler          │
│  - @ExceptionHandler(Duplicate) │
│  - HTTP 409 반환                 │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  HTTP 409 Response          │
│  {                          │
│    "code": "C006",          │
│    "message": "중복 발급"    │
│  }                          │
└─────────────────────────────┘
```

**코드 위치:**
- Handler: `GlobalExceptionHandler.java:29-38`

---

### 🔹 Flow 5: Validation 예외 (400 Bad Request)

```
Client Request (POST /api/auth/signup)
Body: { "email": "invalid" }
         │
         ▼
┌──────────────────────────────┐
│ Controller                   │
│  - @Valid 검증 실패           │
│  - MethodArgumentNotValid... │
└────────┬─────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ GlobalExceptionHandler          │
│  - @ExceptionHandler(MANVE)     │
│  - HTTP 400 반환                 │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  HTTP 400 Response          │
│  {                          │
│    "code": "COMMON001",     │
│    "message": "email: ..."  │
│  }                          │
└─────────────────────────────┘
```

**코드 위치:**
- Handler: `GlobalExceptionHandler.java:97-112`

---

## 에러 코드 체계

### 📌 인증 관련 (AUTH*)

| 코드 | 예외 클래스 | 발생 위치 | HTTP | 설명 |
|---|---|---|---|---|
| **AUTH001** | InvalidTokenException | Filter / UseCase | 401 | 유효하지 않은 토큰 |
| **AUTH002** | ExpiredTokenException | Filter | 401 | 만료된 토큰 |
| **AUTH003** | LoggedOutTokenException | UseCase | 401 | 로그아웃된 토큰 (Redis 미존재) |
| **AUTH004** | TokenMismatchException | UseCase | 401 | 토큰 불일치 |

### 📌 상품 관련 (P*)

| 코드 | 예외 클래스 | 발생 위치 | HTTP | 설명 |
|---|---|---|---|---|
| **P001** | ProductNotFoundException | Domain | 400 | 상품을 찾을 수 없음 |
| **P002** | InsufficientStockException | Domain | 400 | 재고 부족 |

### 📌 주문 관련 (O*)

| 코드 | 예외 클래스 | 발생 위치 | HTTP | 설명 |
|---|---|---|---|---|
| **O001** | InvalidOrderQuantityException | Domain | 400 | 유효하지 않은 주문 수량 |
| **O002** | OrderNotFoundException | Domain | 400 | 주문을 찾을 수 없음 |

### 📌 결제 관련 (PAY*)

| 코드 | 예외 클래스 | 발생 위치 | HTTP | 설명 |
|---|---|---|---|---|
| **PAY001** | InsufficientBalanceException | Domain | 400 | 잔액 부족 |
| **PAY002** | PaymentFailedException | Domain | 400 | 결제 실패 |

### 📌 쿠폰 관련 (C*)

| 코드 | 예외 클래스 | 발생 위치 | HTTP | 설명 |
|---|---|---|---|---|
| **C001** | CouponSoldOutException | UseCase | 400 | 쿠폰 품절 |
| **C002** | InvalidCouponException | Domain | 400 | 유효하지 않은 쿠폰 |
| **C003** | CouponExpiredException | Domain | 400 | 만료된 쿠폰 |
| **C004** | CouponAlreadyUsedException | Domain | 400 | 이미 사용된 쿠폰 |
| **C005** | CouponIssueLockException | UseCase | 400 | 쿠폰 발급 처리 중 |
| **C006** | DuplicateCouponIssueException | UseCase | **409** | 중복 발급 |

### 📌 공통 에러 (COMMON*)

| 코드 | 발생 위치 | HTTP | 설명 |
|---|---|---|---|
| **COMMON001** | Validation | 400 | 필수 파라미터 누락 |
| **COMMON002** | IllegalArgument | 400 | 잘못된 요청 형식 |
| **COMMON003** | OptimisticLock | 409 | 동시성 충돌 |
| **COMMON004** | Exception | 500 | 서버 내부 오류 |

---

## 레이어별 예외 처리

### 🔷 Filter Layer (필터 계층)

**위치:** `infrastructure/config/security/`

**역할:**
- JWT 토큰 검증
- 인증 실패 시 401 응답

**처리 방식:**
```java
// JwtAuthenticationFilter.java
try {
    if (!jwtProvider.validate(token)) {
        fail(request, response, "INVALID_TOKEN");
    }
} catch (ExpiredJwtException e) {
    fail(request, response, "EXPIRED_TOKEN", e);
}

// JwtAuthenticationEntryPoint.java
@Override
public void commence(...) {
    String errorType = authException.getMessage();
    String code = errorType.equals("EXPIRED_TOKEN") ? "AUTH002" : "AUTH001";
    // 401 Response
}
```

**응답 예시:**
```json
{
  "code": "AUTH001",
  "message": "유효하지 않은 토큰입니다"
}
```

---

### 🔷 Presentation Layer (컨트롤러 계층)

**위치:** `presentation/controller/`

**역할:**
- 요청 매핑
- `@Valid` 검증
- UseCase 호출

**처리 방식:**
```java
@PostMapping("/signup")
public ResponseEntity<?> signup(@Valid @RequestBody RegisterUserRequest request) {
    registerUserUseCase.execute(request);
    return ResponseEntity.ok(...);
}
```

**예외 발생:**
- `MethodArgumentNotValidException` - @Valid 실패
- `ConstraintViolationException` - @PathVariable 검증 실패

---

### 🔷 Application Layer (유스케이스 계층)

**위치:** `application/*/usecase/`

**역할:**
- 비즈니스 로직 조율
- 도메인 서비스 호출
- 비즈니스 예외 발생

**처리 방식:**
```java
public LoginUserResponse execute(LoginUserRequest request) {
    if (!jwtProvider.validate(refreshToken)) {
        throw new InvalidTokenException("유효하지 않은 Refresh Token입니다");
    }

    if (!refreshTokenRedisService.validate(userId, refreshToken)) {
        throw new LoggedOutTokenException("로그아웃된 토큰입니다");
    }
}
```

---

### 🔷 Domain Layer (도메인 계층)

**위치:** `domain/*/exception/`

**역할:**
- 도메인 규칙 검증
- 도메인 예외 발생

**처리 방식:**
```java
// Coupon 엔티티
public void use() {
    if (this.isUsed) {
        throw new CouponAlreadyUsedException("이미 사용된 쿠폰입니다");
    }
    if (this.isExpired()) {
        throw new CouponExpiredException("만료된 쿠폰입니다");
    }
}
```

---

### 🔷 Global Exception Handler

**위치:** `presentation/exception/GlobalExceptionHandler.java`

**역할:**
- 모든 예외를 HTTP 응답으로 변환
- 통일된 에러 응답 형식 제공

**처리 우선순위:**

1. **DuplicateCouponIssueException** → 409 Conflict
2. **BusinessException (AUTH로 시작)** → 401 Unauthorized
3. **BusinessException (기타)** → 400 Bad Request
4. **MethodArgumentNotValidException** → 400 Bad Request
5. **ConstraintViolationException** → 400 Bad Request
6. **IllegalArgumentException** → 400 Bad Request
7. **OptimisticLockingFailureException** → 409 Conflict
8. **Exception** → 500 Internal Server Error

**코드:**
```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    ErrorResponse errorResponse = new ErrorResponse(e.getCode(), e.getMessage());

    // 인증 관련 예외는 401 반환
    if (e.getCode().startsWith("AUTH")) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    // 그 외 비즈니스 예외는 400 반환
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
}
```

---

## 예외 응답 형식

### 표준 에러 응답

```json
{
  "code": "AUTH001",
  "message": "유효하지 않은 토큰입니다"
}
```

**ErrorResponse 클래스:**
```java
public record ErrorResponse(
    String code,
    String message
) {}
```

---

## 예외 처리 모범 사례

### ✅ DO

1. **도메인 예외는 도메인 계층에서 발생**
   ```java
   // domain/coupon/entity/Coupon.java
   public void use() {
       if (isUsed) throw new CouponAlreadyUsedException();
   }
   ```

2. **비즈니스 예외는 UseCase에서 발생**
   ```java
   // application/auth/usecase/RefreshAccessTokenUseCase.java
   if (!validate(token)) {
       throw new InvalidTokenException();
   }
   ```

3. **예외 메시지는 명확하게**
   ```java
   throw new InvalidTokenException("유효하지 않은 Refresh Token입니다");
   ```

4. **에러 코드는 ErrorCode enum 사용**
   ```java
   protected BusinessException(ErrorCode errorCode) {
       super(errorCode.getMessage());
   }
   ```

### ❌ DON'T

1. **Controller에서 비즈니스 예외 발생 금지**
   ```java
   // ❌ Bad
   @PostMapping("/products")
   public ResponseEntity<?> create() {
       if (stock < 0) throw new BusinessException(); // Controller에서 발생
   }
   ```

2. **예외 메시지에 기술 정보 노출 금지**
   ```java
   // ❌ Bad
   throw new Exception("NullPointerException at line 123");

   // ✅ Good
   throw new BusinessException(ErrorCode.COMMON004);
   ```

3. **Exception을 직접 catch 후 무시 금지**
   ```java
   // ❌ Bad
   try {
       doSomething();
   } catch (Exception e) {
       // 무시
   }
   ```

---

## 테스트 가이드

### 예외 처리 테스트 예시

```java
@Test
void 유효하지_않은_토큰_요청시_401_반환() {
    // given
    String invalidToken = "invalid.jwt.token";

    // when
    ResultActions result = mockMvc.perform(
        get("/api/products")
            .header("Authorization", "Bearer " + invalidToken)
    );

    // then
    result.andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTH001"))
          .andExpect(jsonPath("$.message").exists());
}
```

---

## 참고 자료

- **코드 위치:**
  - 예외 클래스: `src/main/java/com/sparta/ecommerce/{domain}/exception/`
  - 예외 핸들러: `src/main/java/com/sparta/ecommerce/presentation/exception/GlobalExceptionHandler.java`
  - 에러 코드: `src/main/java/com/sparta/ecommerce/common/exception/ErrorCode.java`

- **관련 문서:**
  - API 설계 문서: `document/API_DESIGN.md`
  - 시퀀스 다이어그램: `document/SEQUENCE_DIAGRAM.md`
