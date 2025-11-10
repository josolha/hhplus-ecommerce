# 이커머스 시스템 아키텍처

## 아키텍처 패턴

이 프로젝트는 **레이어드 아키텍처(Layered Architecture)** 패턴을 따릅니다.

### 레이어드 아키텍처란?

애플리케이션을 수평적 계층으로 분리하여 각 계층이 명확한 책임을 갖도록 구성하는 패턴입니다.

**핵심 원칙:**
- 각 계층은 명확한 책임을 가짐
- 상위 계층은 하위 계층을 의존 (단방향 의존성)
- 각 계층은 자신의 바로 아래 계층만 호출

---

## 계층 구조

```
┌─────────────────────────────────────┐
│     Presentation Layer              │  ← HTTP 요청/응답 처리
│  (Controller, ExceptionHandler)     │
└─────────────────────────────────────┘
              ↓ 의존
┌─────────────────────────────────────┐
│     Application Layer               │  ← 비즈니스 유스케이스
│      (Service)                      │
└─────────────────────────────────────┘
              ↓ 의존
┌─────────────────────────────────────┐
│     Domain Layer                    │  ← 핵심 비즈니스 로직
│  (Entity, Value Object, Exception)  │
└─────────────────────────────────────┘
              ↑ 의존
┌─────────────────────────────────────┐
│     Infrastructure Layer            │  ← 외부 기술 구현
│  (Repository Interface & Impl)      │
└─────────────────────────────────────┘
```

---

## 디렉토리 구조

```
src/main/java/com/sparta/ecommerce/
├── presentation/              # Presentation Layer
│   ├── controller/           # REST API 엔드포인트
│   │   ├── ProductController.java
│   │   ├── OrderController.java
│   │   ├── CouponController.java
│   │   ├── UserController.java
│   │   ├── CartController.java
│   │   └── AdminController.java
│   └── exception/            # HTTP 예외 처리
│       ├── GlobalExceptionHandler.java
│       └── ErrorResponse.java
│
├── application/               # Application Layer
│   └── service/              # 비즈니스 유스케이스
│       ├── ProductService.java
│       ├── OrderService.java
│       ├── CouponService.java
│       ├── UserService.java
│       └── CartService.java
│
├── domain/                    # Domain Layer
│   ├── common/               # 공통 도메인 요소
│   │   └── exception/        # 비즈니스 예외
│   │       ├── BusinessException.java
│   │       └── ErrorCode.java
│   │
│   ├── product/              # 상품 도메인
│   │   ├── Product.java      # Entity
│   │   └── exception/
│   │       ├── ProductNotFoundException.java
│   │       └── InsufficientStockException.java
│   │
│   ├── order/                # 주문 도메인
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── exception/
│   │       ├── OrderNotFoundException.java
│   │       └── InvalidOrderQuantityException.java
│   │
│   ├── coupon/               # 쿠폰 도메인
│   │   ├── Coupon.java
│   │   ├── UserCoupon.java
│   │   └── exception/
│   │       ├── CouponSoldOutException.java
│   │       ├── CouponExpiredException.java
│   │       ├── InvalidCouponException.java
│   │       └── CouponAlreadyUsedException.java
│   │
│   ├── user/                 # 사용자 도메인
│   │   ├── User.java
│   │   ├── Balance.java
│   │   └── exception/
│   │       ├── UserNotFoundException.java
│   │       └── InsufficientBalanceException.java
│   │
│   └── cart/                 # 장바구니 도메인
│       ├── Cart.java
│       ├── CartItem.java
│       └── exception/
│
└── infrastructure/            # Infrastructure Layer
    ├── repository/           # Repository 인터페이스
    │   ├── ProductRepository.java
    │   ├── OrderRepository.java
    │   ├── CouponRepository.java
    │   ├── UserRepository.java
    │   └── CartRepository.java
    │
    └── memory/               # 인메모리 구현체
        ├── InMemoryProductRepository.java
        ├── InMemoryOrderRepository.java
        ├── InMemoryCouponRepository.java
        ├── InMemoryUserRepository.java
        └── InMemoryCartRepository.java
```

---

## 각 계층의 책임

### 1. Presentation Layer

**책임:**
- HTTP 요청을 받아서 Application Layer로 전달
- Application Layer의 결과를 HTTP 응답으로 변환
- 예외를 HTTP 에러 응답으로 변환

**규칙:**
- Controller는 비즈니스 로직을 포함하지 않음
- Service를 호출하고 결과를 반환하는 역할만
- Request/Response DTO 변환

**예시:**
```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    @GetMapping("/{productId}")
    public ResponseEntity<?> getProduct(@PathVariable String productId) {
        return ResponseEntity.ok(productService.getProduct(productId));
    }
}
```

---

### 2. Application Layer

**책임:**
- 비즈니스 유스케이스 구현
- 여러 도메인 객체를 조합하여 업무 흐름 처리
- 트랜잭션 경계 설정

**규칙:**
- Domain 객체를 사용하여 비즈니스 로직 수행
- Repository를 통해 데이터 접근
- 도메인 로직은 가능한 Domain Layer에 위임

**예시:**
```java
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public Order createOrder(CreateOrderRequest request) {
        // 1. 도메인 객체 조회
        Product product = productRepository.findById(request.getProductId());

        // 2. 도메인 로직 실행 (재고 차감)
        product.decreaseStock(request.getQuantity());

        // 3. 주문 생성
        Order order = new Order(...);
        return orderRepository.save(order);
    }
}
```

---

### 3. Domain Layer

**책임:**
- 핵심 비즈니스 로직 구현
- 비즈니스 규칙 검증
- 도메인 예외 정의

**규칙:**
- Entity는 비즈니스 로직을 포함
- 외부 기술(DB, 프레임워크)에 의존하지 않음
- 다른 계층을 의존하지 않음 (순수 Java)

**예시:**
```java
public class Product {
    private String productId;
    private String name;
    private int stock;

    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new InsufficientStockException(quantity, this.stock);
        }
        this.stock -= quantity;
    }
}
```

---

### 4. Infrastructure Layer

**책임:**
- 외부 시스템과의 연동 구현
- 데이터 저장소 접근 (현재는 인메모리)
- Repository 인터페이스 구현

**규칙:**
- Domain Layer의 Repository 인터페이스를 구현
- 현재는 Map을 사용한 인메모리 구현
- 나중에 DB 연동 시 구현체만 교체

**예시:**
```java
public interface ProductRepository {
    Product findById(String productId);
    void save(Product product);
}

@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> store = new ConcurrentHashMap<>();

    @Override
    public Product findById(String productId) {
        Product product = store.get(productId);
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }
        return product;
    }
}
```

---

## 의존성 방향

```
Presentation ──→ Application ──→ Domain ←── Infrastructure
```

**핵심:**
- Presentation은 Application만 의존
- Application은 Domain만 의존
- Infrastructure는 Domain을 의존 (인터페이스 구현)
- Domain은 아무것도 의존하지 않음 (순수)

**장점:**
- 변경의 영향 범위 최소화
- 테스트 용이성
- 유연한 구조

---

## Exception 처리 구조

### 계층별 예외 처리

```
┌─────────────────────────────────────┐
│  Presentation Layer                 │
│  GlobalExceptionHandler             │  ← 모든 예외를 HTTP 응답으로 변환
│  @ExceptionHandler                  │
└─────────────────────────────────────┘
              ↑ 예외 전파
┌─────────────────────────────────────┐
│  Application Layer                  │
│  Service                            │  ← 예외를 그대로 전파
└─────────────────────────────────────┘
              ↑ 예외 전파
┌─────────────────────────────────────┐
│  Domain Layer                       │
│  Entity                             │  ← 비즈니스 예외 발생
│  throw BusinessException            │
└─────────────────────────────────────┘
```

### 예외 계층 구조

```
BusinessException (abstract)
  ├── ProductNotFoundException
  ├── InsufficientStockException
  ├── OrderNotFoundException
  ├── CouponSoldOutException
  ├── CouponExpiredException
  └── InsufficientBalanceException
```

---

## 데이터 흐름

### 조회 흐름 (GET /api/products/{id})

```
1. ProductController.getProduct(id)
         ↓
2. ProductService.getProduct(id)
         ↓
3. ProductRepository.findById(id)
         ↓
4. InMemoryProductRepository (Map에서 조회)
         ↓
5. Product (Entity 반환)
         ↓
6. Service → Controller → HTTP Response
```

### 생성/수정 흐름 (POST /api/orders)

```
1. OrderController.createOrder(request)
         ↓
2. OrderService.createOrder(request)
         ↓
3. Product.decreaseStock()  ← 도메인 로직 실행
         ↓
4. User.decreaseBalance()   ← 도메인 로직 실행
         ↓
5. Order order = new Order()
         ↓
6. OrderRepository.save(order)
         ↓
7. InMemoryOrderRepository (Map에 저장)
         ↓
8. Order 반환 → Controller → HTTP Response
```

---

## 테스트 전략

### 1. Domain Layer 테스트
```java
@Test
void 재고_차감_성공() {
    // given
    Product product = new Product("P001", "노트북", 10);

    // when
    product.decreaseStock(5);

    // then
    assertThat(product.getStock()).isEqualTo(5);
}
```

### 2. Application Layer 테스트
```java
@Test
void 주문_생성_성공() {
    // given
    ProductRepository mockProductRepo = mock(ProductRepository.class);
    OrderService orderService = new OrderService(mockProductRepo);

    // when
    Order order = orderService.createOrder(request);

    // then
    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
}
```

### 3. Presentation Layer 테스트
```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Test
    void 상품_조회_성공() throws Exception {
        mockMvc.perform(get("/api/products/P001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value("P001"));
    }
}
```

---

## 주요 설계 결정

### 1. 왜 레이어드 아키텍처인가?
- 학습 목적: 가장 기본적이고 이해하기 쉬운 패턴
- 명확한 계층 분리로 책임 구분 학습
- 다른 아키텍처(헥사고날, 클린)의 기초

### 2. 왜 인메모리 저장소인가?
- DB 연동 전 비즈니스 로직에 집중
- 빠른 테스트 실행
- 나중에 구현체만 교체하면 DB 연동 가능

### 3. 예외를 Domain에 둔 이유
- 예외는 비즈니스 규칙의 일부
- Domain이 순수하게 유지되면서도 규칙 표현
- 다른 계층에서 재사용 가능

---

## 다음 단계 (TODO)

### STEP 5: 기본 구현
- [ ] 도메인 모델 구현 (Entity, Value Object)
- [ ] Repository 인터페이스 및 구현체
- [ ] Application Service 구현
- [ ] Controller와 Service 연동
- [ ] 단위 테스트 작성 (커버리지 70%)

### STEP 6: 동시성 제어
- [ ] 선착순 쿠폰 발급 동시성 제어
- [ ] 재고 차감 동시성 제어
- [ ] 통합 테스트 작성
- [ ] 동시성 제어 분석 문서 작성

---

## 참고 자료

- [API 설계 문서](../api/API_DESIGN.md)
- [시퀀스 다이어그램](./SEQUENCE_DIAGRAM.md)
- [ERD](./ERD.md)
- [프로젝트 가이드](../../CLAUDE.md)
