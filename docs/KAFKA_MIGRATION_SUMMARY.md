# Kafka 전환 완료 요약

## 📌 개요

Redis Queue 기반 쿠폰 발급 시스템을 **Kafka 기반으로 완전히 전환**했습니다.

**작업 날짜**: 2025-12-18
**변경 범위**: 쿠폰 발급 비동기 처리 레이어
**엔드포인트**: `POST /api/coupons/{couponId}/issue/queue` (동일 유지)

---

## ✅ 구현 완료 항목

### 1. 설계 문서
- ✅ `docs/KAFKA_COUPON_DESIGN.md` 작성
  - Kafka 도입 배경 및 이유
  - 아키텍처 설계 (다이어그램 포함)
  - 파티션 전략 (couponId를 메시지 키로 사용)
  - 성능 개선 예상치

### 2. Kafka 인프라
- ✅ Kafka Topic 생성: `coupon-issue-request`
  - Partition: 3개
  - Replication Factor: 1 (개발 환경)
- ✅ docker-compose.yml 활용 (기존)

### 3. 코드 구현

#### 신규 파일 (4개)
```
infrastructure/kafka/
├── message/
│   └── CouponIssueMessage.java        # 메시지 DTO (record)
├── producer/
│   └── CouponKafkaProducer.java       # Kafka Producer
└── consumer/
    └── CouponKafkaConsumer.java       # Kafka Consumer (concurrency=3)
```

#### 수정 파일 (2개)
```
application/coupon/
├── service/
│   └── CouponQueueService.java        # addToIssuedSetOnly() 메서드 추가
└── usecase/
    └── IssueCouponWithQueueUseCase.java  # Kafka 방식으로 변경
```

#### 비활성화 파일 (1개)
```
application/coupon/worker/
└── CouponWorker.java                  # @Component 주석 처리
```

---

## 🔄 변경 사항 비교

### Before: Redis Queue 방식

```
[사용자 요청]
     ↓
[IssueCouponWithQueueUseCase]
     ↓
[Redis Set] ← 중복 체크
     ↓
[Redis List] ← Queue (LPUSH)
     ↓
[CouponWorker (3 threads)] ← BRPOP
     ↓
[CouponIssueProcessor]
     ↓
[Database]
```

**특징**:
- Redis List를 Queue로 사용
- CouponWorker가 BRPOP으로 메시지 소비
- 메시지 휘발성 (Redis 재시작 시 유실)

### After: Kafka 방식

```
[사용자 요청]
     ↓
[IssueCouponWithQueueUseCase]
     ↓
[Redis Set] ← 중복 체크 (유지)
     ↓
[Kafka Topic: coupon-issue-request]
  ├─ Partition 0 → Consumer 1
  ├─ Partition 1 → Consumer 2
  └─ Partition 2 → Consumer 3
     ↓
[CouponIssueProcessor] ← 재사용
     ↓
[Database]
```

**개선점**:
- Kafka Topic을 Queue로 사용
- CouponKafkaConsumer가 메시지 소비 (3개 스레드)
- 메시지 영속성 보장 (디스크 저장)
- 파티션별 병렬 처리 (처리량 3배)

---

## 🎯 핵심 설계 결정

### 1. Redis Set 유지
**결정**: Redis Set은 그대로 유지하여 중복 체크 수행

**이유**:
- API 응답 속도 유지 (< 50ms)
- Kafka만 사용 시 Consumer에서 중복 체크 → 응답 지연
- Redis Set은 O(1) 시간복잡도로 빠름

### 2. couponId를 메시지 키로 사용
**결정**: `kafkaTemplate.send(TOPIC, couponId, message)`

**이유**:
- Kafka는 `hash(key) % partitionCount`로 파티션 결정
- 같은 couponId는 항상 같은 파티션으로 라우팅
- 파티션 내에서 순차 처리 보장 (선착순 보장)
- 다른 쿠폰은 다른 파티션에서 병렬 처리

**예시**:
```
couponId = "A" → hash("A") % 3 = 0 → Partition 0
couponId = "A" → hash("A") % 3 = 0 → Partition 0 (동일!)
couponId = "B" → hash("B") % 3 = 1 → Partition 1 (병렬)
```

### 3. Consumer Concurrency = Partition 수
**결정**: `@KafkaListener(concurrency = "3")`

**이유**:
- Partition 3개 = Consumer 3개 (1:1 매칭)
- 각 Consumer가 하나의 Partition 담당
- 최대 병렬 처리 효율

### 4. CouponIssueProcessor 재사용
**결정**: 기존 코드 그대로 재사용

**이유**:
- 트랜잭션 처리 로직 동일
- 예외 처리 로직 검증됨
- 코드 중복 방지

---

## 📊 기대 효과

| 지표 | Before (Redis Queue) | After (Kafka) | 개선 |
|------|---------------------|---------------|------|
| **API 응답 시간** | < 50ms | < 50ms | 동일 |
| **처리량** | 33 TPS (단일 Worker) | 100 TPS (3 Partition) | **3배↑** |
| **메시지 유실률** | 높음 (Redis 장애 시) | 낮음 (디스크 저장) | **안정성↑** |
| **확장성** | 제한적 (Worker 경합) | 높음 (Consumer Group) | **무한 확장** |
| **모니터링** | 제한적 | Kafka Manager 활용 | **운영성↑** |

---

## 🧪 테스트 방법

### 1. Kafka 상태 확인
```bash
# Kafka 컨테이너 확인
docker ps | grep kafka

# Topic 확인
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe --topic coupon-issue-request
```

**예상 출력**:
```
Topic: coupon-issue-request	PartitionCount: 3	ReplicationFactor: 1
	Partition: 0	Leader: 1	Replicas: 1	Isr: 1
	Partition: 1	Leader: 1	Replicas: 1	Isr: 1
	Partition: 2	Leader: 1	Replicas: 1	Isr: 1
```

### 2. 애플리케이션 실행
```bash
./gradlew bootRun
```

**확인 로그**:
```
[Kafka Consumer] 3개 스레드 시작
쿠폰 큐 Worker 시작 (없음 - 비활성화됨)
```

### 3. 쿠폰 발급 요청 테스트
```bash
# 쿠폰 생성 (사전 작업)
curl -X POST http://localhost:8080/api/coupons \
  -H "Content-Type: application/json" \
  -d '{
    "name": "신년 할인 쿠폰",
    "discountType": "PERCENT",
    "discountValue": 10,
    "totalQuantity": 100,
    "minOrderAmount": 10000,
    "expiresAt": "2025-12-31T23:59:59"
  }'

# 응답에서 couponId 확인 후 사용

# 쿠폰 발급 요청
curl -X POST http://localhost:8080/api/coupons/{쿠폰ID}/issue/queue \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1"}'
```

**예상 응답**:
```json
{
  "success": true,
  "message": "쿠폰 발급 요청이 접수되었습니다. 순차적으로 처리됩니다.",
  "queueSize": 0
}
```

**확인 로그**:
```
[Kafka Producer] 쿠폰 발급 메시지 발행 성공 - couponId: xxx, userId: user1, partition: 0
[Kafka Consumer] 쿠폰 발급 메시지 수신 - couponId: xxx, userId: user1
쿠폰 발급 성공: userId=user1, couponId=xxx
[Kafka Consumer] 쿠폰 발급 처리 완료 - couponId: xxx, userId: user1
```

### 4. 동시성 테스트 (선택)
```bash
# 100명이 동시에 같은 쿠폰 발급 요청
for i in {1..100}; do
  curl -X POST http://localhost:8080/api/coupons/{쿠폰ID}/issue/queue \
    -H "Content-Type: application/json" \
    -d "{\"userId\": \"user$i\"}" &
done
wait

# 결과 확인
# - 100건 모두 즉시 응답 (< 1초)
# - 중복 요청 없음
# - 재고 차감 정확 (100개 발급 후 소진)
```

### 5. Kafka UI로 모니터링 (선택)
```
http://localhost:8008
```

**Kafdrop 화면에서 확인**:
- Topic: `coupon-issue-request`
- Messages: 발행된 메시지 확인
- Partitions: 파티션별 메시지 분포 확인

---

## 🚨 주의사항

### 1. Redis는 여전히 필요함
- **용도**: 중복 체크용 (Redis Set)
- **주의**: Redis 장애 시 중복 발급 가능 (DB에서 최종 방어)

### 2. CouponWorker는 비활성화됨
- **상태**: `@Component` 주석 처리
- **롤백 방법**: 주석 제거 후 재시작

### 3. Kafka는 반드시 실행 필요
```bash
docker-compose up -d kafka
```

### 4. Topic이 없으면 에러 발생
- **증상**: `UnknownTopicOrPartitionException`
- **해결**: Topic 생성 명령 실행

---

## 📁 파일 구조

```
ecommerce-core/
├── docs/
│   ├── KAFKA_COUPON_DESIGN.md          # 🆕 상세 설계 문서
│   └── KAFKA_MIGRATION_SUMMARY.md      # 🆕 이 문서
│
├── src/main/java/.../
│   ├── infrastructure/kafka/
│   │   ├── message/
│   │   │   └── CouponIssueMessage.java        # 🆕 메시지 DTO
│   │   ├── producer/
│   │   │   └── CouponKafkaProducer.java       # 🆕 Producer
│   │   └── consumer/
│   │       └── CouponKafkaConsumer.java       # 🆕 Consumer
│   │
│   └── application/coupon/
│       ├── service/
│       │   └── CouponQueueService.java        # 🔧 수정 (메서드 추가)
│       ├── usecase/
│       │   └── IssueCouponWithQueueUseCase.java  # 🔧 수정 (Kafka 사용)
│       └── worker/
│           └── CouponWorker.java              # ⚠️ 비활성화
│
└── docker-compose.yml                         # 기존 (Kafka 포함)
```

---

## 🎓 학습 포인트

### 1. 왜 Kafka인가?
- **메시지 영속성**: 디스크 저장으로 장애 시에도 데이터 보존
- **확장성**: Consumer Group으로 무한 확장 가능
- **파티션 전략**: 메시지 키로 순서 보장 + 병렬 처리

### 2. 파티션 전략의 중요성
- 메시지 키 선택이 성능과 정합성을 결정
- couponId를 키로 선택하여 같은 쿠폰은 순차, 다른 쿠폰은 병렬

### 3. 기존 코드 재사용의 가치
- CouponIssueProcessor 재사용으로 개발 시간 단축
- 검증된 로직 유지로 안정성 확보

---

## 🔮 향후 개선 방안

### Phase 2: Dead Letter Queue (DLQ)
- 3회 재시도 실패 시 별도 Topic으로 이동
- 수동 확인 및 처리

### Phase 3: Consumer 동적 확장
- Kubernetes HPA로 자동 스케일링
- Kafka Consumer Lag 기반 확장

### Phase 4: 성능 테스트
- JMeter로 부하 테스트
- Before/After 성능 비교 지표 수집

### Phase 5: 모니터링 대시보드
- Prometheus + Grafana
- Kafka Lag, 처리 속도, 실패율 실시간 모니터링

---

## ✅ 완료 체크리스트

- [x] 설계 문서 작성
- [x] Kafka Topic 생성
- [x] Producer 구현
- [x] Consumer 구현
- [x] 기존 코드 수정
- [x] Worker 비활성화
- [x] 빌드 성공 확인
- [ ] 통합 테스트 (수동)
- [ ] 부하 테스트 (선택)

---

**작성자**: Ecommerce Core Team
**버전**: 1.0
**다음 작업**: 통합 테스트 및 성능 측정
