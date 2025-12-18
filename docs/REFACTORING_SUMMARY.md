# 코드 정리 및 리팩토링 요약

## 📌 개요

Kafka 전환 후 불필요한 Redis Queue 관련 코드를 정리하고, 클래스 이름을 더 명확하게 리팩토링했습니다.

**작업 날짜**: 2025-12-18
**목적**: 코드 가독성 향상, 유지보수성 개선

---

## 🗑️ 삭제된 파일

### 1. CouponWorker.java (완전 삭제)
**경로**: `application/coupon/worker/CouponWorker.java`

**삭제 이유**:
- Redis Queue (BRPOP) 방식 전용 Worker
- Kafka Consumer로 완전히 대체됨
- 더 이상 사용되지 않음

**대체**:
- `CouponKafkaConsumer.java` - Kafka 메시지 소비

---

### 2. 테스트 파일 (Deprecated 처리)

#### CouponQueueConcurrencyTest.java.deprecated
**경로**: `test/.../CouponQueueConcurrencyTest.java.deprecated`

**사유**: Redis Queue 방식 동시성 테스트로 더 이상 유효하지 않음

#### BlockingQueueCouponTest.java.deprecated
**경로**: `test/.../BlockingQueueCouponTest.java.deprecated`

**사유**: Redis Blocking Queue 테스트로 더 이상 유효하지 않음

**참고**: 파일 확장자에 `.deprecated` 추가하여 보존 (롤백 가능)

---

## ✨ 새로 생성된 파일

### CouponIssueRedisService.java
**경로**: `application/coupon/service/CouponIssueRedisService.java`

**목적**: Redis Set 기반 중복 체크 전용 서비스

**메서드**:
```java
public Long addToIssuedSet(String couponId, String userId)
public boolean hasRequested(String couponId, String userId)
public void removeFromIssuedSet(String couponId, String userId)
```

**변경 이유**:
- 기존 `CouponQueueService`가 Queue + Set 혼재
- 역할 분리: Redis Set 중복 체크만 담당
- 명확한 네이밍으로 가독성 향상

---

## 🔧 수정된 파일

### 1. IssueCouponWithQueueUseCase.java
**변경 전**:
```java
private final CouponQueueService queueService;
Long added = queueService.addToIssuedSetOnly(couponId, userId);
```

**변경 후**:
```java
private final CouponIssueRedisService redisService;
Long added = redisService.addToIssuedSet(couponId, userId);
```

**개선점**:
- 더 명확한 의존성 (`redisService`)
- 메서드 이름 간소화 (`addToIssuedSetOnly` → `addToIssuedSet`)

---

### 2. CouponIssueProcessor.java
**변경 전**:
```java
private final CouponQueueService queueService;
queueService.removeFromIssuedSet(couponId, userId);
```

**변경 후**:
```java
private final CouponIssueRedisService redisService;
redisService.removeFromIssuedSet(couponId, userId);
```

**주석 업데이트**:
```java
/**
 * 쿠폰 발급 처리 서비스 (트랜잭션 전용)
 * Kafka Consumer에서 호출하여 트랜잭션 컨텍스트에서 쿠폰을 발급합니다.
 */
```

---

### 3. CreateCouponUseCase.java
**변경 전**:
```java
private final CouponWorker couponWorker;

// 새로 생성된 쿠폰에 대한 Worker 시작
couponWorker.startWorkerForCoupon(savedCoupon.getCouponId());
log.info("쿠폰 Worker 시작: couponId={}", savedCoupon.getCouponId());
```

**변경 후**:
```java
// CouponWorker 의존성 제거

// Kafka Consumer가 자동으로 메시지를 처리하므로 별도 Worker 시작 불필요
```

**개선점**:
- 불필요한 의존성 제거
- Kafka Consumer는 애플리케이션 시작 시 자동 실행되므로 수동 시작 불필요

---

### 4. CouponQueueService.java (@Deprecated)
**변경**:
```java
/**
 * ⚠️ DEPRECATED: Kafka 방식으로 전환됨
 *
 * 대체 클래스:
 * - CouponIssueRedisService: Redis Set 중복 체크 전용
 * - CouponKafkaProducer/Consumer: 메시지 큐잉
 *
 * @deprecated Kafka 전환으로 더 이상 사용되지 않음
 */
@Deprecated
@Service
public class CouponQueueService {
    // 기존 코드 유지 (테스트 코드 호환성 위해)
}
```

**상태**: Deprecated 처리 (완전 삭제하지 않음)

**이유**:
- 기존 테스트 코드가 참조할 수 있음
- 롤백 시 쉽게 복구 가능
- 점진적 마이그레이션 지원

---

## 📊 변경 전후 비교

### 클래스 구조

#### Before (Redis Queue 방식)
```
CouponQueueService
├── addToQueue()           # Queue + Set 둘 다 처리
├── popFromQueue()         # Worker 전용
├── blockingPopFromQueue() # Worker 전용
├── getQueueSize()
├── hasRequested()
└── removeFromIssuedSet()

CouponWorker               # Redis Queue 소비
└── startWorkerForCoupon()

CreateCouponUseCase
└── couponWorker.start()   # 수동 Worker 시작
```

#### After (Kafka 방식)
```
CouponIssueRedisService    # Redis Set 전용
├── addToIssuedSet()       # 중복 체크만
├── hasRequested()
└── removeFromIssuedSet()

CouponKafkaConsumer        # Kafka 메시지 소비
└── @KafkaListener         # 자동 시작

CreateCouponUseCase
└── (Worker 시작 불필요)  # Kafka Consumer 자동 실행
```

---

## 🎯 리팩토링 효과

### 1. 명확한 책임 분리
- **CouponIssueRedisService**: Redis Set 중복 체크만
- **CouponKafkaProducer**: 메시지 발행만
- **CouponKafkaConsumer**: 메시지 소비만

### 2. 가독성 향상
- `CouponQueueService` → `CouponIssueRedisService` (더 명확)
- `addToIssuedSetOnly()` → `addToIssuedSet()` (간결)

### 3. 유지보수성 개선
- 불필요한 코드 제거 (CouponWorker)
- Deprecated 처리로 점진적 마이그레이션
- 명확한 주석으로 의도 파악 용이

### 4. 자동화
- Worker 수동 시작 불필요
- Kafka Consumer 자동 실행
- 운영 복잡도 감소

---

## 📁 최종 파일 구조

```
application/coupon/
├── service/
│   ├── CouponIssueRedisService.java      # 🆕 Redis Set 전용
│   ├── CouponIssueService.java           # (기존)
│   ├── CouponIssueProcessor.java         # 🔧 수정
│   └── CouponQueueService.java           # ⚠️ Deprecated
├── usecase/
│   ├── IssueCouponWithQueueUseCase.java  # 🔧 수정
│   └── CreateCouponUseCase.java          # 🔧 수정
└── worker/
    └── (삭제됨)

infrastructure/kafka/
├── message/
│   └── CouponIssueMessage.java
├── producer/
│   └── CouponKafkaProducer.java
└── consumer/
    └── CouponKafkaConsumer.java

test/.../coupon/
├── CouponQueueConcurrencyTest.java.deprecated  # ⚠️ Deprecated
└── BlockingQueueCouponTest.java.deprecated     # ⚠️ Deprecated
```

---

## ✅ 체크리스트

- [x] CouponWorker 삭제
- [x] worker 디렉토리 삭제
- [x] CouponIssueRedisService 생성
- [x] IssueCouponWithQueueUseCase 리팩토링
- [x] CouponIssueProcessor 리팩토링
- [x] CreateCouponUseCase 리팩토링
- [x] CouponQueueService Deprecated 처리
- [x] 테스트 파일 Deprecated 처리
- [x] 빌드 성공 확인

---

## 🚨 주의사항

### 1. CouponQueueService는 삭제 안 함
- **이유**: 기존 코드 호환성
- **상태**: @Deprecated 처리
- **향후**: 완전히 사용되지 않음 확인 후 삭제 고려

### 2. 테스트 파일 복구 방법
```bash
# .deprecated 확장자 제거
mv CouponQueueConcurrencyTest.java.deprecated \
   CouponQueueConcurrencyTest.java
```

### 3. 롤백 방법
1. Git에서 이전 커밋으로 복구
2. `@Deprecated` 제거
3. CouponWorker 복구

---

## 🎓 학습 포인트

### 1. 점진적 마이그레이션
- 완전 삭제보다 Deprecated 처리로 안전성 확보
- 테스트 파일 보존으로 롤백 가능성 유지

### 2. 단일 책임 원칙 (SRP)
- 하나의 클래스는 하나의 책임만
- `CouponQueueService` → Queue + Set 혼재 (SRP 위반)
- `CouponIssueRedisService` → Set만 (SRP 준수)

### 3. 명확한 네이밍
- 이름만 보고도 역할을 알 수 있도록
- `QueueService` → 큐 관리인지 애매
- `IssueRedisService` → 발급 관련 Redis 서비스 명확

---

**작성자**: Ecommerce Core Team
**버전**: 1.0
**다음 작업**: 통합 테스트 및 Kafka 성능 측정
