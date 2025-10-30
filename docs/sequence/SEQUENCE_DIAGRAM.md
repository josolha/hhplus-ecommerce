# 이커머스 시퀀스 다이어그램

## 1. 상품 조회 및 장바구니 담기

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Client as 클라이언트
    participant API as API Server
    participant ProductDB as 상품 DB
    participant CartDB as 장바구니 DB

    User->>Client: 상품 목록 페이지 접속
    Client->>API: GET /api/products
    API->>ProductDB: 상품 목록 조회
    ProductDB-->>API: 상품 목록 반환
    API-->>Client: 상품 목록 응답 (가격, 재고 포함)
    Client-->>User: 상품 목록 표시

    User->>Client: 특정 상품 상세 조회
    Client->>API: GET /api/products/{productId}
    API->>ProductDB: 상품 상세 조회
    ProductDB-->>API: 상품 상세 정보
    API-->>Client: 상품 상세 응답
    Client-->>User: 상품 상세 표시

    User->>Client: 장바구니 담기 (수량 선택)
    Client->>API: GET /api/products/{productId}/stock
    API->>ProductDB: 실시간 재고 확인
    ProductDB-->>API: 현재 재고 수량
    API-->>Client: 재고 정보 응답

    alt 재고 충분
        Client->>API: POST /api/cart/items<br/>{userId, productId, quantity}
        API->>ProductDB: 재고 재확인
        ProductDB-->>API: 재고 OK
        API->>CartDB: 장바구니 항목 추가
        CartDB-->>API: 저장 완료
        API-->>Client: 장바구니 추가 성공
        Client-->>User: "장바구니에 담았습니다"
    else 재고 부족
        API-->>Client: 재고 부족 에러 (P002)
        Client-->>User: "재고가 부족합니다"
    end
```

---

## 2. 주문 및 결제 플로우

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Client as 클라이언트
    participant API as API Server
    participant CartDB as 장바구니 DB
    participant CouponDB as 쿠폰 DB
    participant ProductDB as 상품 DB
    participant UserDB as 사용자 DB
    participant OrderDB as 주문 DB
    participant Queue as 외부 전송 큐
    participant External as 외부 시스템

    User->>Client: 주문하기 클릭
    Client->>API: GET /api/cart?userId={userId}
    API->>CartDB: 장바구니 조회
    CartDB-->>API: 장바구니 항목 목록
    API-->>Client: 장바구니 데이터
    Client-->>User: 주문 상품 목록 표시

    Note over Client,ProductDB: 재고 확인 먼저 수행
    Client->>API: 재고 확인 요청 (각 상품별)
    API->>ProductDB: 재고 확인
    ProductDB-->>API: 재고 수량 반환

    alt 재고 부족
        API-->>Client: 재고 부족 응답
        Client-->>User: "재고가 부족합니다"
        Note over User: 주문 프로세스 종료
    else 재고 충분
        API-->>Client: 재고 충분 확인

        User->>Client: 쿠폰 선택
        Client->>API: GET /api/users/coupons?userId={userId}&status=available
        API->>CouponDB: 사용 가능한 쿠폰 조회
        CouponDB-->>API: 쿠폰 목록
        API-->>Client: 쿠폰 목록 응답
        Client-->>User: 사용 가능 쿠폰 표시

        opt 쿠폰 적용
            User->>Client: 쿠폰 선택
            Client->>API: POST /api/coupons/validate<br/>{userId, couponId, orderAmount}
            API->>CouponDB: 쿠폰 유효성 검증
            CouponDB-->>API: 유효성 결과
            API-->>Client: 할인 금액 계산 결과
            Client-->>User: 할인 적용된 최종 금액 표시
        end

        User->>Client: 결제하기 클릭
        Client->>API: POST /api/orders<br/>{userId, items, couponId}

        Note over API: 트랜잭션 시작

        API->>ProductDB: 재고 재확인 (동시성 제어)
        ProductDB-->>API: 재고 OK

        API->>UserDB: 잔액 확인
        alt 잔액 부족
            UserDB-->>API: 잔액 부족
            Note over API: 트랜잭션 롤백
            API-->>Client: 잔액 부족 에러 (PAY001)
            Client-->>User: "잔액이 부족합니다"
        else 잔액 충분
            UserDB-->>API: 잔액 OK

            API->>ProductDB: 재고 차감
            ProductDB-->>API: 차감 완료

            API->>UserDB: 잔액 차감
            UserDB-->>API: 차감 완료

            opt 쿠폰 사용
                API->>CouponDB: 쿠폰 상태 → USED
                CouponDB-->>API: 업데이트 완료
            end

            API->>OrderDB: 주문 저장
            OrderDB-->>API: 주문 ID 반환

            API->>CartDB: 장바구니 비우기
            CartDB-->>API: 삭제 완료

            Note over API: 트랜잭션 커밋

            API-->>Client: 주문 성공 응답<br/>{orderId, finalAmount, remainingBalance}
            Client-->>User: "주문이 완료되었습니다"

            Note over API,External: 비동기 처리
            API->>Queue: 외부 전송 작업 등록
            Queue->>External: POST 주문 데이터

            alt 전송 성공
                External-->>Queue: 전송 성공
                Queue->>OrderDB: synced = true 업데이트
            else 전송 실패
                External-->>Queue: 전송 실패
                Queue->>OrderDB: 재시도 정보 저장
                Note over Queue: 재시도 큐에 등록<br/>(주문은 정상 처리됨)
            end
        end
    end
```

---

## 3. 쿠폰 선착순 발급

```mermaid
sequenceDiagram
    actor User1 as 사용자1
    actor User2 as 사용자2
    participant Client1 as 클라이언트1
    participant Client2 as 클라이언트2
    participant API as API Server
    participant CouponDB as 쿠폰 DB
    participant Lock as 분산 락 (Redis)

    User1->>Client1: 쿠폰 목록 조회
    User2->>Client2: 쿠폰 목록 조회

    Client1->>API: GET /api/coupons
    Client2->>API: GET /api/coupons

    API->>CouponDB: 발급 가능한 쿠폰 조회
    CouponDB-->>API: 쿠폰 목록 (remainingQuantity 포함)
    API-->>Client1: 쿠폰 목록 응답
    API-->>Client2: 쿠폰 목록 응답

    Client1-->>User1: 남은 수량: 1개 표시
    Client2-->>User2: 남은 수량: 1개 표시

    Note over User1,User2: 동시에 쿠폰 발급 요청

    User1->>Client1: 쿠폰 발급 클릭
    User2->>Client2: 쿠폰 발급 클릭

    par 동시 요청
        Client1->>API: POST /api/coupons/{couponId}/issue<br/>{userId: user1}
        Client2->>API: POST /api/coupons/{couponId}/issue<br/>{userId: user2}
    end

    API->>Lock: 쿠폰 발급 락 획득 시도 (user1)
    Lock-->>API: 락 획득 성공 (user1)

    API->>Lock: 쿠폰 발급 락 획득 시도 (user2)
    Lock-->>API: 락 대기 (user2)

    Note over API: user1 요청 처리 시작

    API->>CouponDB: SELECT remainingQuantity FOR UPDATE
    CouponDB-->>API: remainingQuantity: 1

    alt 수량 있음
        API->>CouponDB: 트랜잭션 시작
        API->>CouponDB: issuedQuantity + 1
        API->>CouponDB: remainingQuantity - 1
        API->>CouponDB: 사용자 쿠폰 발급 기록 저장
        CouponDB-->>API: 저장 완료
        API->>CouponDB: 트랜잭션 커밋

        API->>Lock: 락 해제 (user1)
        API-->>Client1: 발급 성공<br/>{userCouponId, couponId, expiresAt}
        Client1-->>User1: "쿠폰이 발급되었습니다"
    end

    Lock-->>API: 락 획득 성공 (user2)

    Note over API: user2 요청 처리 시작

    API->>CouponDB: SELECT remainingQuantity FOR UPDATE
    CouponDB-->>API: remainingQuantity: 0

    alt 수량 소진
        API->>Lock: 락 해제 (user2)
        API-->>Client2: 수량 소진 에러 (C001)<br/>{error: "COUPON_SOLD_OUT"}
        Client2-->>User2: "쿠폰이 모두 소진되었습니다"
    end

    Note over API,CouponDB: 중복 발급 방지

    User1->>Client1: 동일 쿠폰 재발급 시도
    Client1->>API: POST /api/coupons/{couponId}/issue<br/>{userId: user1}
    API->>CouponDB: 사용자의 쿠폰 발급 이력 확인
    CouponDB-->>API: 이미 발급됨
    API-->>Client1: 이미 발급 에러 (C004)<br/>{error: "ALREADY_ISSUED"}
    Client1-->>User1: "이미 발급받은 쿠폰입니다"
```

---

## 4. 잔액 충전 플로우

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Client as 클라이언트
    participant API as API Server
    participant UserDB as 사용자 DB
    participant PaymentGateway as 결제 게이트웨이

    User->>Client: 잔액 충전 페이지 접속
    Client->>API: GET /api/users/balance?userId={userId}
    API->>UserDB: 현재 잔액 조회
    UserDB-->>API: 현재 잔액
    API-->>Client: 잔액 정보
    Client-->>User: 현재 잔액 표시

    User->>Client: 충전 금액 입력 (예: 50,000원)
    Client-->>User: 충전 금액 확인

    User->>Client: 충전하기 클릭
    Client->>API: POST /api/users/balance/charge<br/>{userId, amount: 50000}

    Note over API,PaymentGateway: 실제 결제 처리 (카드/계좌이체 등)
    API->>PaymentGateway: 결제 요청

    alt 결제 성공
        PaymentGateway-->>API: 결제 성공

        Note over API: 트랜잭션 시작
        API->>UserDB: SELECT balance FOR UPDATE
        UserDB-->>API: 현재 잔액 (예: 10,000원)

        API->>UserDB: UPDATE balance = 60,000원
        UserDB-->>API: 업데이트 완료

        API->>UserDB: 충전 이력 저장
        UserDB-->>API: 저장 완료
        Note over API: 트랜잭션 커밋

        API-->>Client: 충전 성공<br/>{previousBalance: 10000, chargedAmount: 50000, currentBalance: 60000}
        Client-->>User: "50,000원이 충전되었습니다<br/>현재 잔액: 60,000원"

    else 결제 실패
        PaymentGateway-->>API: 결제 실패 (카드 한도 초과 등)
        API-->>Client: 결제 실패 에러
        Client-->>User: "결제에 실패했습니다<br/>카드를 확인해주세요"
    end
```

---

## 5. 인기 상품 조회 (최근 3일 Top 5)

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Client as 클라이언트
    participant API as API Server
    participant Cache as Redis Cache
    participant OrderDB as 주문 DB
    participant ProductDB as 상품 DB

    User->>Client: 인기 상품 페이지 접속
    Client->>API: GET /api/products/popular?days=3&limit=5

    Note over API,Cache: 캐싱 전략 (5분 TTL)
    API->>Cache: 캐시 조회 (key: popular:3days:5)

    alt 캐시 HIT
        Cache-->>API: 캐시된 인기 상품 데이터
        API-->>Client: 인기 상품 목록 응답
        Client-->>User: Top 5 상품 표시

    else 캐시 MISS
        Cache-->>API: 캐시 없음

        Note over API,OrderDB: 최근 3일 주문 집계
        API->>OrderDB: SELECT productId, COUNT(*) as orderCount<br/>FROM orders<br/>WHERE createdAt >= NOW() - INTERVAL 3 DAY<br/>GROUP BY productId<br/>ORDER BY orderCount DESC<br/>LIMIT 5
        OrderDB-->>API: 상품별 주문 수

        API->>ProductDB: 상품 상세 정보 조회 (IN query)
        ProductDB-->>API: 상품 정보 (이름, 가격, 재고 등)

        Note over API: 데이터 가공
        API->>API: 주문 수 + 상품 정보 병합<br/>순위(rank) 계산

        API->>Cache: 결과 캐싱 (TTL: 5분)
        Cache-->>API: 캐싱 완료

        API-->>Client: 인기 상품 목록 응답<br/>{period: {days: 3, startDate, endDate}, products: [...]}
        Client-->>User: Top 5 상품 표시<br/>(순위, 상품명, 주문 수 등)
    end
```

---

## 6. 예외 처리 시나리오

### 6.1 재고 부족 상세 처리

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Client as 클라이언트
    participant API as API Server
    participant ProductDB as 상품 DB
    participant NotificationService as 알림 서비스

    User->>Client: 결제하기 클릭
    Client->>API: POST /api/orders<br/>{items: [{productId: "P001", quantity: 5}]}

    Note over API: 트랜잭션 시작
    API->>ProductDB: SELECT stock FROM products WHERE id = 'P001' FOR UPDATE
    ProductDB-->>API: stock: 2 (요청: 5개, 현재: 2개)

    alt 재고 부족
        Note over API: 트랜잭션 롤백

        API->>ProductDB: 상품 정보 조회 (상품명 등)
        ProductDB-->>API: 상품 상세 정보

        API-->>Client: 재고 부족 에러 (HTTP 400)<br/>{<br/>  error: "INSUFFICIENT_STOCK",<br/>  message: "재고가 부족합니다",<br/>  insufficientItems: [{<br/>    productId: "P001",<br/>    productName: "노트북",<br/>    requestedQuantity: 5,<br/>    availableStock: 2<br/>  }]<br/>}

        Client-->>User: "노트북의 재고가 부족합니다<br/>요청: 5개 / 현재 재고: 2개"

        opt 재입고 알림 신청
            User->>Client: 재입고 알림 신청
            Client->>API: POST /api/products/P001/restock-notification<br/>{userId, email}
            API->>NotificationService: 재입고 알림 등록
            NotificationService-->>API: 등록 완료
            API-->>Client: 알림 신청 완료
            Client-->>User: "재입고 시 알려드리겠습니다"
        end
    end
```

### 6.2 잔액 부족 상세 처리

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Client as 클라이언트
    participant API as API Server
    participant ProductDB as 상품 DB
    participant UserDB as 사용자 DB

    User->>Client: 결제하기 클릭
    Client->>API: POST /api/orders<br/>{userId: "user1", items: [...], finalAmount: 100000}

    Note over API: 트랜잭션 시작

    API->>ProductDB: 재고 확인
    ProductDB-->>API: 재고 OK

    API->>UserDB: SELECT balance FROM users WHERE id = 'user1' FOR UPDATE
    UserDB-->>API: balance: 30000 (필요: 100,000원)

    alt 잔액 부족
        Note over API: 트랜잭션 롤백

        API-->>Client: 잔액 부족 에러 (HTTP 400)<br/>{<br/>  error: "INSUFFICIENT_BALANCE",<br/>  message: "잔액이 부족합니다",<br/>  requiredAmount: 100000,<br/>  currentBalance: 30000,<br/>  shortfall: 70000<br/>}

        Client-->>User: "잔액이 부족합니다<br/>필요 금액: 100,000원<br/>현재 잔액: 30,000원<br/>부족 금액: 70,000원"

        Client-->>User: [잔액 충전하기] 버튼 표시

        opt 잔액 충전
            User->>Client: 잔액 충전하기 클릭
            Note over Client: 잔액 충전 플로우로 이동
        end
    end
```

### 6.3 외부 전송 실패 및 재시도

```mermaid
sequenceDiagram
    actor User as 사용자
    participant API as API Server
    participant OrderDB as 주문 DB
    participant Queue as 재시도 큐
    participant External as 외부 시스템
    participant Scheduler as 스케줄러

    Note over User,API: 주문 완료 (트랜잭션 커밋 완료)
    User->>API: 주문 완료됨

    Note over API,External: 비동기 외부 전송 시도 #1
    API->>Queue: 전송 작업 등록<br/>{orderId, attempt: 1}
    Queue->>External: POST /api/orders<br/>{orderId, items, amount...}

    alt 전송 실패 (네트워크 오류, 타임아웃 등)
        External-->>Queue: 500 Internal Server Error<br/>또는 타임아웃

        Queue->>OrderDB: UPDATE orders<br/>SET synced = false,<br/>    syncAttempts = 1,<br/>    lastSyncError = 'Network timeout',<br/>    nextRetryAt = NOW() + 5분
        OrderDB-->>Queue: 업데이트 완료

        Note over Queue: 5분 후 재시도 예약

        Note over Scheduler: 5분 경과
        Scheduler->>Queue: 재시도 작업 확인
        Queue->>Scheduler: 재시도 대상: orderId

        Note over Scheduler,External: 재시도 #2
        Scheduler->>External: POST /api/orders (재시도)

        alt 재시도 실패
            External-->>Scheduler: 실패
            Scheduler->>OrderDB: UPDATE syncAttempts = 2,<br/>    nextRetryAt = NOW() + 15분
            Note over Scheduler: 15분 후 재시도 (백오프 전략)

        else 재시도 성공
            External-->>Scheduler: 200 OK
            Scheduler->>OrderDB: UPDATE synced = true,<br/>    syncedAt = NOW()
            OrderDB-->>Scheduler: 완료
            Note over OrderDB: 전송 완료!
        end

        Note over OrderDB: 최대 3회 재시도 후<br/>실패 시 관리자 알림

    else 전송 성공
        External-->>Queue: 200 OK
        Queue->>OrderDB: UPDATE synced = true,<br/>    syncedAt = NOW()
        OrderDB-->>Queue: 완료
    end

    Note over User: 사용자는 전송 성공/실패와 무관하게<br/>주문이 정상 완료됨
```

---

## 다이어그램 설명

### 1. 상품 조회 및 장바구니 담기
- 상품 목록/상세 조회 시 재고 정보 포함
- 장바구니 담기 전 실시간 재고 확인
- 재고 부족 시 에러 처리

### 2. 주문 및 결제 플로우
- 장바구니 조회 → 재고 확인 → 쿠폰 선택 → 주문 생성
- 재고/잔액 검증 후 차감 (트랜잭션)
- 외부 시스템 전송은 비동기 처리
- **중요**: 외부 전송 실패해도 주문은 정상 처리

### 3. 쿠폰 선착순 발급
- 동시성 제어 (분산 락 사용)
- 선착순 발급 (remainingQuantity 차감)
- 중복 발급 방지
- 수량 소진 시 에러 처리

### 4. 잔액 충전 플로우
- 결제 게이트웨이 연동
- 트랜잭션으로 잔액 업데이트
- 충전 이력 저장
- 결제 실패 시 에러 처리

### 5. 인기 상품 조회
- Redis 캐싱 전략 (5분 TTL)
- 최근 N일 주문 데이터 집계
- 상품 정보 병합 및 순위 계산
- 성능 최적화

### 6. 예외 처리 시나리오
- **재고 부족**: 상세 에러 정보 제공, 재입고 알림 옵션
- **잔액 부족**: 부족 금액 표시, 충전 유도
- **외부 전송 실패**: 재시도 큐 등록, 백오프 전략, 최대 3회 재시도
