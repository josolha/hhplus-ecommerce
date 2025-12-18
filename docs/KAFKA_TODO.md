# Kafka 과제 정리

## 📋 현재 프로젝트 상태

### 기존 구조 (변경 전)
```
OrderEventListener.java
└── @TransactionalEventListener(AFTER_COMMIT)
    └── externalDataPlatformService.sendOrderData()  ← Mock API 호출
        └── Thread.sleep(2000) + 10% 실패 시뮬레이션
```

**파일 위치:**
- `src/main/java/com/sparta/ecommerce/application/order/listener/OrderEventListener.java:26`
- `src/main/java/com/sparta/ecommerce/infrastructure/external/ExternalDataPlatformService.java`

---

## 🎯 STEP 17: 카프카 기초 학습 및 활용

### 체크리스트

#### 1. 카프카 개념 학습 및 문서 작성
- [ ] Kafka 핵심 개념 정리
  - [ ] Producer (메시지 발행자)
  - [ ] Consumer (메시지 소비자)
  - [ ] Topic (메시지 분류 기준)
  - [ ] Partition (병렬 처리 단위)
  - [ ] Broker (카프카 서버)
  - [ ] Consumer Group (소비 주체 그룹)
  - [ ] Replication (고가용성 보장)
  - [ ] Offset (메시지 처리 위치)
  - [ ] **KRaft vs Zookeeper** (최신 Kafka는 Zookeeper 불필요)

- [ ] 문서 작성: `docs/KAFKA_CONCEPT.md`
  - [ ] 구성요소 간 데이터 흐름 다이어그램
  - [ ] Kafka 장단점 정리
  - [ ] 왜 대용량 트래픽 처리에 Kafka를 사용하는가?
  - [ ] KRaft 모드 설명 (Kafka 3.0+ 기본 모드)

**산출물:** `docs/KAFKA_CONCEPT.md`

---

#### 2. Docker로 Kafka 환경 구성

**✅ 권장: KRaft 모드 (Zookeeper 없이 실행)**

- [ ] `docker-compose.yml` 작성 - **KRaft 모드 (간단)**
  ```yaml
  version: '3.8'
  services:
    kafka:
      image: apache/kafka:latest
      container_name: kafka
      ports:
        - "9092:9092"
      environment:
        # KRaft 모드 설정 (Zookeeper 불필요)
        KAFKA_NODE_ID: 1
        KAFKA_PROCESS_ROLES: broker,controller
        KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
        KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
        KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
        KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
        KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
        KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
        KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
        KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
        KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
        KAFKA_NUM_PARTITIONS: 3
  ```

<details>
<summary>📌 기존 방식: Zookeeper 모드 (클릭하여 보기)</summary>

```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```
</details>

- [ ] Kafka 실행 확인
  ```bash
  docker-compose up -d
  docker ps  # Kafka 실행 확인
  docker logs kafka  # 로그 확인
  ```

- [ ] CLI로 메시지 송수신 테스트
  ```bash
  # Topic 생성
  docker exec -it <kafka-container> kafka-topics --create \
    --bootstrap-server localhost:9092 \
    --topic test-topic \
    --partitions 3 \
    --replication-factor 1

  # Producer 테스트
  docker exec -it <kafka-container> kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic test-topic

  # Consumer 테스트 (다른 터미널)
  docker exec -it <kafka-container> kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic test-topic \
    --from-beginning
  ```

**산출물:** `docker-compose.yml` + CLI 테스트 로그 스크린샷

---

#### 3. Spring Kafka 의존성 추가 및 설정
- [ ] `build.gradle` 의존성 추가
  ```gradle
  dependencies {
      // Kafka
      implementation 'org.springframework.kafka:spring-kafka'
      testImplementation 'org.springframework.kafka:spring-kafka-test'
  }
  ```

- [ ] `application.yml` Kafka 설정 추가
  ```yaml
  spring:
    kafka:
      bootstrap-servers: localhost:9092
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      consumer:
        group-id: ecommerce-order-group
        key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
        value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
        properties:
          spring.json.trusted.packages: "*"
        auto-offset-reset: earliest
  ```

- [ ] Kafka Config 클래스 작성 (필요시)
  - `src/main/java/com/sparta/ecommerce/infrastructure/config/KafkaConfig.java`

**산출물:** 수정된 `build.gradle`, `application.yml`

---

#### 4. 주문 완료 이벤트를 Kafka로 발행하도록 변경

**핵심 작업: Mock API 호출 → Kafka 메시지 발행으로 변경**

##### 4-1. Kafka Producer 작성
- [ ] `OrderKafkaProducer.java` 생성
  ```java
  @Component
  @RequiredArgsConstructor
  public class OrderKafkaProducer {
      private static final String TOPIC = "order-completed";
      private final KafkaTemplate<String, OrderCompletedMessage> kafkaTemplate;

      public void publishOrderCompleted(Order order) {
          OrderCompletedMessage message = OrderCompletedMessage.from(order);
          kafkaTemplate.send(TOPIC, order.getOrderId().toString(), message);
          log.info("[Kafka Producer] 주문 완료 메시지 발행 - Order ID: {}", order.getOrderId());
      }
  }
  ```

- [ ] `OrderCompletedMessage.java` DTO 작성
  ```java
  public record OrderCompletedMessage(
      Long orderId,
      Long userId,
      BigDecimal finalAmount,
      LocalDateTime createdAt
  ) {
      public static OrderCompletedMessage from(Order order) {
          return new OrderCompletedMessage(
              order.getOrderId(),
              order.getUserId(),
              order.getFinalAmount(),
              order.getCreatedAt()
          );
      }
  }
  ```

##### 4-2. OrderEventListener 수정
- [ ] **기존 코드 (OrderEventListener.java:26)**
  ```java
  // 변경 전
  externalDataPlatformService.sendOrderData(event.order());
  ```

- [ ] **변경할 코드**
  ```java
  // 변경 후
  orderKafkaProducer.publishOrderCompleted(event.order());
  ```

##### 4-3. Kafka Consumer 작성
- [ ] `OrderKafkaConsumer.java` 생성
  ```java
  @Component
  @Slf4j
  public class OrderKafkaConsumer {

      @KafkaListener(topics = "order-completed", groupId = "ecommerce-order-group")
      public void consumeOrderCompleted(OrderCompletedMessage message) {
          log.info("[Kafka Consumer] 주문 완료 메시지 수신 - Order ID: {}, User ID: {}, Amount: {}",
              message.orderId(), message.userId(), message.finalAmount());

          // TODO: 실제 외부 데이터 플랫폼 전송은 나중에 구현
          // 현재는 로그만 출력
      }
  }
  ```

##### 4-4. 통합 테스트
- [ ] 주문 생성 API 호출
- [ ] Kafka Producer 로그 확인
- [ ] Kafka Consumer 로그 확인
- [ ] 메시지가 정상적으로 발행/소비되는지 검증

**산출물:**
- Producer/Consumer 구현 코드
- 실행 로그 스크린샷
- `docs/KAFKA_ORDER_FLOW.md` (메시지 흐름 정리)

---

## 🎯 STEP 18: 카프카를 활용한 비즈니스 프로세스 개선

### 체크리스트

#### 1. 선착순 쿠폰 발급 Kafka 설계

##### 현재 구조 분석
- [ ] **기존 방식:** Redis 분산락 사용
  - 장점: 동시성 제어 가능
  - 단점: Lock 경합으로 처리량 제한, Lock 획득 실패 시 사용자 경험 저하

##### Kafka 기반 개선 설계
- [ ] **설계 방향**
  - 메시지 키: `couponId` (같은 쿠폰은 같은 파티션으로)
  - 파티션 수: 3개 (병렬 처리)
  - 순차 보장: 같은 쿠폰 ID는 같은 파티션에서 순차 처리
  - 별도 Lock 불필요

- [ ] **아키텍처 구성**
  ```
  [Client] → [API Server] → [Kafka Topic: coupon-issue-request]
                                   ↓
                              [Partition 0] → [Consumer 1]
                              [Partition 1] → [Consumer 2]
                              [Partition 2] → [Consumer 3]
                                   ↓
                              [쿠폰 발급 처리]
  ```

- [ ] **설계 문서 작성: `docs/KAFKA_COUPON_DESIGN.md`**
  - [ ] 시퀀스 다이어그램 (Mermaid)
  - [ ] 파티션 전략 설명
  - [ ] 메시지 키 설계 (`couponId`)
  - [ ] Consumer 병렬 처리 전략
  - [ ] 기존 방식 대비 개선 사항
  - [ ] 처리량 향상 예측

**산출물:** `docs/KAFKA_COUPON_DESIGN.md`

---

#### 2. 쿠폰 발급 Kafka 기반 구현

##### 2-1. Topic 및 Partition 설정
- [ ] Topic 생성: `coupon-issue-request`
  ```bash
  docker exec -it <kafka-container> kafka-topics --create \
    --bootstrap-server localhost:9092 \
    --topic coupon-issue-request \
    --partitions 3 \
    --replication-factor 1
  ```

##### 2-2. Producer 구현
- [ ] `CouponKafkaProducer.java`
  ```java
  @Component
  public class CouponKafkaProducer {
      private static final String TOPIC = "coupon-issue-request";

      public void publishCouponIssueRequest(Long couponId, Long userId) {
          // 메시지 키: couponId (같은 쿠폰은 같은 파티션으로)
          String key = couponId.toString();
          CouponIssueMessage message = new CouponIssueMessage(couponId, userId);
          kafkaTemplate.send(TOPIC, key, message);
      }
  }
  ```

- [ ] API에서 Producer 호출하도록 변경
  ```java
  // 기존: Redis Lock + 즉시 발급
  // 변경: Kafka 메시지 발행 후 즉시 응답
  ```

##### 2-3. Consumer 구현
- [ ] `CouponKafkaConsumer.java`
  ```java
  @Component
  public class CouponKafkaConsumer {

      @KafkaListener(
          topics = "coupon-issue-request",
          groupId = "coupon-issue-group",
          concurrency = "3"  // Consumer 3개 (파티션 수와 동일)
      )
      public void consumeCouponIssueRequest(CouponIssueMessage message) {
          // 순차적으로 쿠폰 발급 처리
          // Lock 없이도 같은 쿠폰은 같은 파티션에서 순차 처리됨
          couponService.issueCoupon(message.couponId(), message.userId());
      }
  }
  ```

##### 2-4. 동시성 테스트
- [ ] 100명이 동시에 같은 쿠폰 발급 요청
- [ ] 초과 발급 발생하지 않는지 검증
- [ ] 처리량 측정 (기존 방식 vs Kafka 방식)

**산출물:**
- Producer/Consumer 구현 코드
- 동시성 테스트 결과
- 성능 비교 표

---

## 📊 평가 기준

### STEP 17 Pass 기준
- [x] Kafka 핵심 개념을 정확히 이해하여 문서 작성
- [x] 어플리케이션에서 Kafka 메시지 발행/소비 가능
- [x] 주문 완료(커밋) 후 Kafka 메시지 발행 구현

### STEP 18 Pass 기준
- [x] 비즈니스 프로세스에 Kafka를 적절히 활용한 설계
- [x] 설계 문서와 동일하게 구현
- [x] 파티션/키 전략을 활용한 동시성 제어

### 도전 항목
- [ ] Producer/Consumer/Partition 수에 따른 데이터 흐름 파악
- [ ] 병렬성, 순차성, 중복처리 전략 포함한 설계
- [ ] 시퀀스 다이어그램으로 시각화
- [ ] 성능 개선 지표 측정 및 비교

---

## 📁 예상 산출물 목록

```
docs/
├── KAFKA_CONCEPT.md           # Kafka 기본 개념 정리
├── KAFKA_ORDER_FLOW.md        # 주문 메시지 흐름 정리
├── KAFKA_COUPON_DESIGN.md     # 쿠폰 발급 Kafka 설계
└── KAFKA_PERFORMANCE.md       # 성능 비교 결과

src/main/java/.../infrastructure/
├── kafka/
│   ├── producer/
│   │   ├── OrderKafkaProducer.java
│   │   └── CouponKafkaProducer.java
│   ├── consumer/
│   │   ├── OrderKafkaConsumer.java
│   │   └── CouponKafkaConsumer.java
│   └── message/
│       ├── OrderCompletedMessage.java
│       └── CouponIssueMessage.java
└── config/
    └── KafkaConfig.java

docker-compose.yml             # Kafka 환경 구성
```

---

## 🔍 핵심 포인트 요약

### Mock API → Kafka 변경 핵심
```java
// ❌ 기존 (OrderEventListener.java:26)
externalDataPlatformService.sendOrderData(event.order());

// ✅ 변경
orderKafkaProducer.publishOrderCompleted(event.order());
```

### 쿠폰 발급 개선 핵심
**기존:** Redis Lock → 동시성 제어하지만 처리량 제한
**개선:** Kafka Partition → 메시지 키(couponId)로 자동 분산, Lock 없이 순차 보장

### 파티션 전략
- **메시지 키 = couponId**
- 같은 쿠폰 → 같은 파티션 → 순차 처리 보장
- 다른 쿠폰 → 다른 파티션 → 병렬 처리로 처리량 향상

---

## ⏰ 학습 시간 예상

- **STEP 17:** 3~5시간
  - Kafka 개념 학습: 1시간
  - 환경 구성 및 테스트: 1시간
  - Spring Kafka 연동: 2시간

- **STEP 18:** 3~5시간
  - 설계 문서 작성: 1시간
  - 구현 및 테스트: 3시간

**총 예상 시간:** 6~10시간
