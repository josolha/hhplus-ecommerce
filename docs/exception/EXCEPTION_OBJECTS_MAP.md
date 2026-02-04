# 예외 처리 객체 관계도

프로젝트의 예외 처리를 담당하는 핵심 객체들의 관계와 역할을 설명합니다.

---

## 📐 전체 객체 관계도

```
┌─────────────────────────────────────────────────────────────────────┐
│                          예외 발생 계층                                │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │             │             │
              ┌─────▼──────┐ ┌───▼────┐ ┌─────▼─────┐
              │ UseCase    │ │ Domain │ │Controller │
              │   예외      │ │  예외   │ │  예외     │
              └─────┬──────┘ └───┬────┘ └─────┬─────┘
                    │             │             │
                    └─────────────┼─────────────┘
                                  │
                         throws   │
                                  ▼
        ┌─────────────────────────────────────────────────────┐
        │         ① BusinessException (추상 클래스)             │
        │    - 모든 비즈니스 예외의 부모                         │
        │    - ErrorCode를 필드로 가짐                          │
        │    - RuntimeException 상속                          │
        ├─────────────────────────────────────────────────────┤
        │  private final ErrorCode errorCode;                │
        │                                                     │
        │  public String getCode() {                         │
        │      return errorCode.getCode();  ───────┐         │
        │  }                                       │         │
        │                                          │         │
        │  public String getMessage() {            │         │
        │      return super.getMessage();          │         │
        │  }                                       │         │
        └──────────────────────────────────────────┼─────────┘
                         │                         │
                         │ has                     │ uses
                         │                         │
                         ▼                         │
        ┌─────────────────────────────────────────┼─────────┐
        │         ② ErrorCode (Enum)              │         │
        │    - 에러 코드와 메시지 정의              │         │
        ├─────────────────────────────────────────┼─────────┤
        │  AUTH001("AUTH001", "유효하지 않은 토큰") ◄──┐      │
        │  AUTH002("AUTH002", "만료된 토큰")         │      │
        │  P001("P001", "상품을 찾을 수 없습니다")    │      │
        │  C001("C001", "쿠폰 소진")                 │      │
        │                                          │      │
        │  private final String code;   ◄──────────┘      │
        │  private final String message;                  │
        │                                                 │
        │  public String getCode() {                      │
        │      return code;                               │
        │  }                                              │
        │                                                 │
        │  public String getMessage() {                   │
        │      return message;                            │
        │  }                                              │
        └─────────────────────────────────────────────────┘
                         │
                         │ 사용됨
                         │
                         ▼
        ┌─────────────────────────────────────────────────────┐
        │    ③ GlobalExceptionHandler (@RestControllerAdvice) │
        │    - 모든 예외를 잡아서 HTTP 응답으로 변환             │
        ├─────────────────────────────────────────────────────┤
        │  @ExceptionHandler(BusinessException.class)        │
        │  public ResponseEntity<ErrorResponse>              │
        │      handleBusinessException(                      │
        │          BusinessException e  ◄────────┐           │
        │      ) {                                │           │
        │                                         │           │
        │      String code = e.getCode(); ────────┼─────┐     │
        │      String msg = e.getMessage(); ──────┼─────┼─┐   │
        │                                         │     │ │   │
        │      ErrorResponse response =           │     │ │   │
        │          new ErrorResponse(code, msg); ─┼─────┼─┼─┐ │
        │                                         │     │ │ │ │
        │      return ResponseEntity              │     │ │ │ │
        │          .status(400)                   │     │ │ │ │
        │          .body(response); ──────────────┼─────┼─┼─┼─┤
        │  }                                      │     │ │ │ │
        └─────────────────────────────────────────┼─────┼─┼─┼─┘
                                                  │     │ │ │
                                     catches      │     │ │ │
                                     exception    │     │ │ │
                                                  │     │ │ │
                                                  │     │ │ creates
                                                  │     │ │ │
                                                  │     │ │ ▼
        ┌─────────────────────────────────────────┼─────┼─┼─────────┐
        │         ④ ErrorResponse (DTO)           │     │ │         │
        │    - HTTP 응답 본문                      │     │ │         │
        ├─────────────────────────────────────────┼─────┼─┼─────────┤
        │  private final String code; ◄───────────┘     │ │         │
        │  private final String message; ◄──────────────┘ │         │
        │  private final LocalDateTime timestamp;         │         │
        │                                                 │         │
        │  public ErrorResponse(String code,              │         │
        │                       String message) {         │         │
        │      this.code = code;                          │         │
        │      this.message = message;                    │         │
        │      this.timestamp = LocalDateTime.now();      │         │
        │  }                                              │         │
        │                                                 │         │
        │  // getters...                                  │         │
        └─────────────────────────────────────────────────┼─────────┘
                                                          │
                                             returns      │
                                                          ▼
                                    ┌─────────────────────────────┐
                                    │   HTTP Response Body        │
                                    ├─────────────────────────────┤
                                    │  {                          │
                                    │    "code": "AUTH001",       │
                                    │    "message": "유효하지...", │
                                    │    "timestamp": "2024-..."  │
                                    │  }                          │
                                    └─────────────────────────────┘
```

---

## 🔗 객체별 상세 관계

### ① BusinessException (추상 클래스)

**위치:** `common/exception/BusinessException.java`

**역할:**
- 모든 비즈니스 예외의 **부모 클래스**
- `ErrorCode`를 **필드**로 보유
- `RuntimeException` 상속

**관계:**
```java
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;  // ◄─── ErrorCode를 필드로 가짐

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());   // ErrorCode의 메시지 사용
        this.errorCode = errorCode;
    }

    public String getCode() {
        return errorCode.getCode();      // ◄─── ErrorCode의 getCode() 호출
    }
}
```

**관계 요약:**
- **has-a** ErrorCode (필드로 보유)
- **is-a** RuntimeException (상속)
- **used by** GlobalExceptionHandler (처리됨)

---

### ② ErrorCode (Enum)

**위치:** `common/exception/ErrorCode.java`

**역할:**
- 모든 에러 코드와 메시지를 **상수**로 정의
- 중앙화된 에러 코드 관리

**구조:**
```java
public enum ErrorCode {
    // 각 상수가 code와 message를 가짐
    AUTH001("AUTH001", "유효하지 않은 토큰입니다"),  // ◄─── 상수 정의
    AUTH002("AUTH002", "만료된 토큰입니다"),
    P001("P001", "상품을 찾을 수 없습니다"),
    // ...

    private final String code;     // ◄─── 에러 코드
    private final String message;  // ◄─── 에러 메시지

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
```

**관계 요약:**
- **used by** BusinessException (생성자 파라미터)
- **provides** code와 message

---

### ③ GlobalExceptionHandler (@RestControllerAdvice)

**위치:** `presentation/exception/GlobalExceptionHandler.java`

**역할:**
- 모든 예외를 **잡아서** HTTP 응답으로 변환
- `BusinessException`에서 정보 추출
- `ErrorResponse` 객체 생성

**처리 흐름:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)  // ◄─── BusinessException 처리
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e) {  // ◄─── 예외 객체 받음

        // 1. BusinessException에서 정보 추출
        String code = e.getCode();        // ◄─── ErrorCode에서 온 값
        String message = e.getMessage();  // ◄─── ErrorCode 또는 커스텀 메시지

        // 2. ErrorResponse 생성
        ErrorResponse errorResponse = new ErrorResponse(code, message);

        // 3. HTTP 응답 반환
        if (code.startsWith("AUTH")) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)  // 401
                .body(errorResponse);
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)  // 400
            .body(errorResponse);
    }
}
```

**관계 요약:**
- **catches** BusinessException (예외 처리)
- **creates** ErrorResponse (DTO 생성)
- **returns** ResponseEntity<ErrorResponse> (HTTP 응답)

---

### ④ ErrorResponse (DTO)

**위치:** `presentation/exception/ErrorResponse.java`

**역할:**
- HTTP 응답 본문 (Body)
- 클라이언트에게 전달되는 최종 형태

**구조:**
```java
public class ErrorResponse {
    private final String code;          // ◄─── ErrorCode에서 온 값
    private final String message;       // ◄─── ErrorCode 또는 커스텀 메시지
    private final LocalDateTime timestamp;  // 응답 시각

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    // getters...
}
```

**JSON 변환:**
```json
{
  "code": "AUTH001",
  "message": "유효하지 않은 토큰입니다",
  "timestamp": "2024-01-26T23:45:00"
}
```

**관계 요약:**
- **created by** GlobalExceptionHandler
- **returned in** ResponseEntity body
- **sent to** Client as JSON

---

## 🔄 데이터 흐름 (End-to-End)

### 예시: 유효하지 않은 토큰 에러

```
Step 1: 예외 발생
─────────────────────────────────────────────────────
RefreshAccessTokenUseCase.java

if (!jwtProvider.validate(refreshToken)) {
    throw new InvalidTokenException("유효하지 않은 Refresh Token입니다");
         ↓
}
         │
         │ InvalidTokenException은 BusinessException 상속
         │
         ▼

┌────────────────────────────────────────────────┐
│ InvalidTokenException                          │
│   extends BusinessException                    │
├────────────────────────────────────────────────┤
│ public InvalidTokenException() {               │
│     super(ErrorCode.AUTH001);  ◄─── ① ErrorCode 사용 │
│ }                                              │
└────────────────────────────────────────────────┘
         │
         │ BusinessException 생성자 호출
         ▼

┌────────────────────────────────────────────────┐
│ BusinessException                              │
├────────────────────────────────────────────────┤
│ protected BusinessException(ErrorCode code) {  │
│     super(code.getMessage()); ◄─── ② ErrorCode에서 메시지 가져옴 │
│     this.errorCode = code;    ◄─── ③ ErrorCode 저장 │
│ }                                              │
└────────────────────────────────────────────────┘


Step 2: 예외 캐치
─────────────────────────────────────────────────────
GlobalExceptionHandler.java

@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handleBusinessException(
    BusinessException e  ◄─── ④ 예외 객체 받음
) {
    String code = e.getCode();        ◄─── ⑤ "AUTH001" (ErrorCode에서)
    String message = e.getMessage();  ◄─── ⑥ "유효하지 않은 Refresh Token입니다"

    ErrorResponse response = new ErrorResponse(code, message);  ◄─── ⑦ DTO 생성

    return ResponseEntity.status(401).body(response);  ◄─── ⑧ HTTP 응답
}


Step 3: HTTP 응답
─────────────────────────────────────────────────────
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "code": "AUTH001",          ◄─── ErrorCode.AUTH001.getCode()
  "message": "유효하지 않은 Refresh Token입니다",  ◄─── 커스텀 메시지
  "timestamp": "2024-01-26T23:45:00"
}
```

---

## 📊 객체 의존성 그래프

```
                     ┌──────────────┐
                     │  ErrorCode   │
                     │   (Enum)     │
                     └───────┬──────┘
                             │
                      사용됨  │ used by
                             │
                ┌────────────▼────────────┐
                │  BusinessException      │
                │   (Abstract Class)      │
                └────────┬────────────────┘
                         │
                  상속됨  │ extended by
                         │
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼────────┐ ┌────▼─────────┐ ┌───▼──────────┐
│InvalidToken    │ │CouponSoldOut │ │Product...    │
│Exception       │ │Exception     │ │Exception     │
└───────┬────────┘ └────┬─────────┘ └───┬──────────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
                   처리됨 │ caught by
                         │
            ┌────────────▼─────────────┐
            │ GlobalExceptionHandler   │
            │  (@RestControllerAdvice) │
            └────────────┬─────────────┘
                         │
                   생성   │ creates
                         │
                ┌────────▼────────┐
                │  ErrorResponse  │
                │     (DTO)       │
                └─────────────────┘
                         │
                         │ returned in
                         ▼
                ┌─────────────────┐
                │ ResponseEntity  │
                │ <ErrorResponse> │
                └─────────────────┘
                         │
                         ▼
                    HTTP Response
                    (JSON)
```

---

## 💡 핵심 포인트

### 1. ErrorCode → BusinessException
```java
// ErrorCode는 BusinessException의 생성자 파라미터
throw new InvalidTokenException();
    ↓
super(ErrorCode.AUTH001);  // ErrorCode 사용
```

### 2. BusinessException → GlobalExceptionHandler
```java
// GlobalExceptionHandler가 BusinessException을 잡음
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    // e에서 정보 추출
}
```

### 3. GlobalExceptionHandler → ErrorResponse
```java
// Handler가 ErrorResponse 생성
ErrorResponse response = new ErrorResponse(
    e.getCode(),      // ErrorCode에서 온 값
    e.getMessage()    // ErrorCode 또는 커스텀 메시지
);
```

### 4. ErrorResponse → JSON
```java
// ErrorResponse가 JSON으로 직렬화되어 클라이언트로 전송
return ResponseEntity.status(401).body(response);
    ↓
{"code": "AUTH001", "message": "...", "timestamp": "..."}
```

---

## 🎯 요약

| 객체 | 역할 | 관계 |
|---|---|---|
| **ErrorCode** | 에러 코드/메시지 상수 정의 | BusinessException에 **사용됨** |
| **BusinessException** | 비즈니스 예외 부모 클래스 | ErrorCode를 **필드로 보유** |
| **GlobalExceptionHandler** | 예외 → HTTP 응답 변환 | BusinessException을 **처리** |
| **ErrorResponse** | HTTP 응답 DTO | GlobalExceptionHandler가 **생성** |

**연결 순서:**
```
ErrorCode → BusinessException → GlobalExceptionHandler → ErrorResponse → HTTP JSON
```
