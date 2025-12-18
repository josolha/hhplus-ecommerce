# Kafka 기반 선착순 쿠폰 발급 설계

## 목차
1. [문서 작성 목적](#1-문서-작성-목적)
2. [현재 시스템 분석](#2-현재-시스템-분석)
3. [Kafka 도입 배경 및 이유](#3-kafka-도입-배경-및-이유)
4. [Kafka 기반 아키텍처 설계](#4-kafka-기반-아키텍처-설계)
5. [메시지 및 파티션 전략](#5-메시지-및-파티션-전략)
6. [동시성 제어 전략](#6-동시성-제어-전략)
7. [구현 세부사항](#7-구현-세부사항)
8. [예상 효과 및 성능 개선](#8-예상-효과-및-성능-개선)
9. [향후 확장 방안](#9-향후-확장-방안)

---

## 1. 문서 작성 목적

### 왜 설계 문서가 필요한가?

이 문서는 단순한 기술 문서가 아닌, **의사결정의 근거**를 남기는 중요한 산출물입니다.

#### 1.1 아키텍처 의사결정 기록 (ADR)
- **Redis Queue에서 Kafka로 전환하는 이유**를 명확히 기록
- 향후 유지보수 시 "왜 이렇게 설계했는지" 이해 가능
- 트레이드오프(장단점)를 명시하여 합리적 선택임을 증명

#### 1.2 팀 커뮤니케이션 도구
- 신규 팀원 온보딩 시 시스템 이해도를 빠르게 향상
- 코드 리뷰 시 설계 의도를 참고하여 더 나은 피드백 가능
- 기획/PM과 기술적 제약사항 공유 용이

#### 1.3 기술 부채 방지
- 설계 없이 구현하면 임시방편(workaround)이 쌓여 기술 부채 누적
- 명확한 설계 방향이 있어야 일관된 코드 작성 가능
- 추후 확장 시 설계 문서를 기반으로 안정적 확장 가능

#### 1.4 학습 및 성장
- 설계 과정에서 깊은 기술 이해 획득
- 문제 해결 과정을 문서화하여 포트폴리오로 활용
- 아키텍처 설계 능력 향상

---

## 2. 현재 시스템 분석

### 2.1 Redis Queue 방식 구조

```
[Client Request]
       ↓
[API Server: IssueCouponWithQueueUseCase]
       ↓
[Redis - 2단계 처리]
  ├─ Redis Set: coupon:issued:{couponId}  (중복 방지)
  └─ Redis List: coupon:queue:{couponId}  (FIFO Queue)
       ↓
[CouponWorker - 3개 스레드]
  └─ BRPOP (Blocking Pop) - 큐에서 꺼내기
       ↓
[CouponIssueProcessor]
  └─ 트랜잭션 내에서 쿠폰 발급
       ↓
[Database]
  ├─ UPDATE coupons (재고 차감)
  └─ INSERT user_coupons (발급 기록)
```

### 2.2 Redis Queue 방식의 장점

| 장점 | 설명 |
|------|------|
| **간단한 구현** | Redis List 자료구조만으로 구현 가능 |
| **빠른 응답** | 큐에 추가만 하고 즉시 응답 (비동기 처리) |
| **순서 보장** | FIFO 구조로 선착순 보장 |
| **분산 락 불필요** | 큐 자체가 순차 처리하므로 Lock 불필요 |

### 2.3 Redis Queue 방식의 한계

#### 2.3.1 메시지 휘발성 (데이터 유실 위험)
- **문제**: Redis는 In-Memory 저장소로, 장애 시 큐 데이터 유실
- **시나리오**: Redis 재시작 시 처리 대기 중인 수천 건의 쿠폰 발급 요청 소실
- **영향도**: 사용자 불만 증가, 재발급 처리 비용 발생

#### 2.3.2 단일 장애점 (Single Point of Failure)
- **문제**: Redis 인스턴스 1대에 의존
- **영향**: Redis 장애 시 전체 쿠폰 발급 시스템 마비
- **해결책**: Redis Cluster/Sentinel 필요 → 운영 복잡도 증가

#### 2.3.3 수평 확장의 어려움
- **문제**: Worker를 여러 서버에 분산 실행 시 동일 큐를 공유
- **결과**: 같은 큐를 여러 Worker가 BRPOP → 경합 발생
- **한계**: Worker 수를 늘려도 처리량 향상 제한적

#### 2.3.4 재처리 메커니즘 부재
- **문제**: 메시지 처리 중 예외 발생 시 재처리 어려움
- **시나리오**: Worker가 DB 연결 실패로 예외 발생 → 메시지 유실
- **Kafka와 비교**: Kafka는 Offset 커밋 실패 시 자동 재처리

#### 2.3.5 모니터링 및 운영 도구 부족
- Redis CLI로 큐 사이즈 확인 가능하지만, 상세 모니터링 어려움
- 메시지 처리 지연, 처리 실패율 등 메트릭 수집 제한적

---

## 3. Kafka 도입 배경 및 이유

### 3.1 왜 Kafka인가?

#### 3.1.1 메시지 영속성 (Durability)
- **선택 이유**: Kafka는 디스크에 메시지를 저장하여 장애 시에도 데이터 보존
- **효과**: Redis 장애 시 수천 건 유실 → Kafka는 장애 복구 후 이어서 처리 가능
- **설정**: `log.retention.hours=168` (7일간 메시지 보관)

#### 3.1.2 분산 아키텍처 (High Availability)
- **선택 이유**: Broker 3대 클러스터 구성 시 1~2대 장애에도 서비스 지속
- **효과**: Single Point of Failure 제거
- **트레이드오프**: 운영 복잡도 증가 (개발 환경에서는 단일 Broker 사용)

#### 3.1.3 파티션 기반 병렬 처리
- **선택 이유**: 파티션 3개 구성 시 3개 Consumer가 독립적으로 병렬 처리
- **효과**: 처리량 3배 향상 (이론적으로)
- **핵심 메커니즘**:
   - 같은 `couponId`는 같은 파티션으로 라우팅 (메시지 키 활용)
   - 파티션 내에서는 순차 처리 보장
   - 서로 다른 쿠폰은 다른 파티션에서 병렬 처리

#### 3.1.4 Consumer Group 기반 확장성
- **선택 이유**: Consumer를 추가하면 자동으로 파티션 재할당 (Rebalancing)
- **효과**: 트래픽 증가 시 Consumer만 추가하면 수평 확장 가능
- **제약사항**: Consumer 수 > 파티션 수 일 때는 일부 Consumer는 Idle

#### 3.1.5 Offset 기반 재처리
- **선택 이유**: Consumer가 메시지 처리 후 명시적으로 Offset 커밋
- **효과**: 처리 실패 시 동일 메시지 재시도 가능
- **예시**: DB 장애로 쿠폰 발급 실패 → Offset 미커밋 → 재시도

#### 3.1.6 강력한 생태계 및 모니터링
- **선택 이유**: Kafka Manager, Prometheus Exporter 등 다양한 도구 존재
- **효과**:
   - 파티션별 Lag 모니터링 (처리 지연 감지)
   - Consumer 처리 속도, 실패율 메트릭 수집
   - Grafana 대시보드로 실시간 모니터링

### 3.2 왜 Redis Queue를 완전히 교체하는가?

#### 3.2.1 하이브리드 방식을 선택하지 않은 이유
- **하이브리드 시나리오**: Redis Set (중복 체크) + Kafka (큐잉)
- **문제점**:
   - Redis와 Kafka 두 시스템 동시 장애 대응 필요
   - 운영 복잡도 증가
   - 두 시스템 간 데이터 불일치 가능성

#### 3.2.2 단계적 전환보다 완전 교체를 선택한 이유
- **판단**: 현재 시스템이 프로덕션 트래픽이 적은 초기 단계
- **리스크**: 단계적 전환 시 코드 복잡도 증가, 롤백 어려움
- **결정**: Clean Cut - 기존 방식 완전 제거 후 Kafka로 일원화

#### 3.2.3 Redis 역할 변경
- **변경 전**: Redis List (Queue) + Redis Set (중복 체크)
- **변경 후**: Redis Set (중복 체크만) + Kafka (큐잉 + 순서 보장)
- **이유**:
   - 중복 체크는 Redis가 여전히 빠름 (O(1) 시간복잡도)
   - API 응답 속도 유지 (Redis Set 체크 후 즉시 응답)
   - Kafka는 큐잉과 메시지 전달에 집중

---

## 4. Kafka 기반 아키텍처 설계

### 4.1 전체 시스템 구조

```
┌─────────────┐
│   Client    │
│  (사용자)    │
└──────┬──────┘
       │ POST /api/coupons/{id}/issue/queue
       ↓
┌─────────────────────────────────────────────────┐
│          API Server (Spring Boot)               │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │ IssueCouponWithQueueUseCase               │ │
│  │   1. Redis Set 중복 체크                   │ │
│  │   2. Kafka 메시지 발행                     │ │
│  │   3. 즉시 202 Accepted 응답                │ │
│  └───────────────────────────────────────────┘ │
└─────────────────┬───────────────────────────────┘
                  │
       ┌──────────┼──────────┐
       │          │          │
       ↓          ↓          ↓
┌──────────────────────────────────────┐
│         Redis (Cluster)              │
│  ┌──────────────────────────────┐   │
│  │ coupon:issued:{couponId}     │   │
│  │ - Type: Set                  │   │
│  │ - Purpose: 중복 방지          │   │
│  │ - 예: {user1, user2, user3}  │   │
│  └──────────────────────────────┘   │
└──────────────────────────────────────┘
                  │
                  ↓ (메시지 발행)
┌──────────────────────────────────────────────────────┐
│              Kafka Cluster                           │
│                                                      │
│  Topic: coupon-issue-request                        │
│  ┌────────────────────────────────────────────────┐ │
│  │ Partition 0 (couponId % 3 == 0)                │ │
│  │  - Messages: [msg1, msg2, msg3, ...]           │ │
│  │  - Consumer: CouponKafkaConsumer-1             │ │
│  └────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────┐ │
│  │ Partition 1 (couponId % 3 == 1)                │ │
│  │  - Messages: [msg4, msg5, msg6, ...]           │ │
│  │  - Consumer: CouponKafkaConsumer-2             │ │
│  └────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────┐ │
│  │ Partition 2 (couponId % 3 == 2)                │ │
│  │  - Messages: [msg7, msg8, msg9, ...]           │ │
│  │  - Consumer: CouponKafkaConsumer-3             │ │
│  └────────────────────────────────────────────────┘ │
└──────────────────┬───────────────────────────────────┘
                   │
       ┌───────────┼───────────┐
       ↓           ↓           ↓
┌──────────────────────────────────────┐
│   Consumer Group: coupon-issue-group │
│                                      │
│  ┌────────────────────────────────┐ │
│  │ CouponKafkaConsumer (3 threads)│ │
│  │   - Thread 1 → Partition 0     │ │
│  │   - Thread 2 → Partition 1     │ │
│  │   - Thread 3 → Partition 2     │ │
│  └────────────────────────────────┘ │
│              ↓                       │
│  ┌────────────────────────────────┐ │
│  │ CouponIssueProcessor           │ │
│  │   - 트랜잭션 처리               │ │
│  │   - 예외 처리                   │ │
│  └────────────────────────────────┘ │
└──────────────┬───────────────────────┘
               │
               ↓
┌──────────────────────────────┐
│       Database (MySQL)       │
│                              │
│  ┌────────────────────────┐ │
│  │ coupons (재고 차감)     │ │
│  │ user_coupons (발급)    │ │
│  └────────────────────────┘ │
└──────────────────────────────┘
```

### 4.2 컴포넌트별 역할

| 컴포넌트 | 역할 | 책임 |
|---------|------|------|
| **IssueCouponWithQueueUseCase** | API 요청 처리 | Redis 중복 체크 → Kafka 발행 → 즉시 응답 |
| **CouponKafkaProducer** | 메시지 발행자 | couponId를 키로 하여 Kafka Topic에 메시지 발행 |
| **Kafka Topic** | 메시지 큐 | 파티션별로 메시지 저장 및 순서 보장 |
| **CouponKafkaConsumer** | 메시지 소비자 | 파티션별 병렬 처리, CouponIssueProcessor 호출 |
| **CouponIssueProcessor** | 비즈니스 로직 | 트랜잭션 내에서 쿠폰 발급, 예외 처리 |
| **Redis Set** | 중복 방지 | 빠른 중복 체크로 API 응답 속도 유지 |

### 4.3 시퀀스 다이어그램

#### 정상 흐름: 쿠폰 발급 성공

```
┌────────┐  ┌─────────┐  ┌───────┐  ┌───────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│ Client │  │   API   │  │ Redis │  │ Kafka │  │ Consumer │  │Processor │  │   DB     │
└───┬────┘  └────┬────┘  └───┬───┘  └───┬───┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
    │            │            │          │           │             │             │
    │ POST /api/coupons/{id}/issue       │           │             │             │
    │───────────>│            │          │           │             │             │
    │            │            │          │           │             │             │
    │            │ SADD coupon:issued:{couponId} {userId}          │             │
    │            │───────────>│          │           │             │             │
    │            │            │          │           │             │             │
    │            │<─ 1 (신규) │          │           │             │             │
    │            │            │          │           │             │             │
    │            │ send(topic, key=couponId, value=message)        │             │
    │            │───────────────────────>│           │             │             │
    │            │            │          │           │             │             │
    │            │<────────── ack ────────│           │             │             │
    │            │            │          │           │             │             │
    │<─ 202 Accepted          │          │           │             │             │
    │            │            │          │           │             │             │
    │            │            │          │ poll()    │             │             │
    │            │            │          │<──────────│             │             │
    │            │            │          │           │             │             │
    │            │            │          │─ message─>│             │             │
    │            │            │          │           │             │             │
    │            │            │          │           │ processSingleIssue(userId, couponId)
    │            │            │          │           │────────────>│             │
    │            │            │          │           │             │             │
    │            │            │          │           │             │ BEGIN TRANSACTION
    │            │            │          │           │             │────────────>│
    │            │            │          │           │             │             │
    │            │            │          │           │             │ UPDATE coupons SET remaining_quantity = remaining_quantity - 1
    │            │            │          │           │             │────────────>│
    │            │            │          │           │             │             │
    │            │            │          │           │             │ INSERT INTO user_coupons (user_id, coupon_id, ...)
    │            │            │          │           │             │────────────>│
    │            │            │          │           │             │             │
    │            │            │          │           │             │<─ COMMIT ───│
    │            │            │          │           │             │             │
    │            │            │          │           │<─ success ──│             │
    │            │            │          │           │             │             │
    │            │            │          │<─ commit offset ────────│             │
    │            │            │          │           │             │             │
```

#### 예외 흐름 1: 중복 발급 차단

```
┌────────┐  ┌─────────┐  ┌───────┐
│ Client │  │   API   │  │ Redis │
└───┬────┘  └────┬────┘  └───┬───┘
    │            │            │
    │ POST /api/coupons/{id}/issue
    │───────────>│            │
    │            │            │
    │            │ SADD coupon:issued:{couponId} {userId}
    │            │───────────>│
    │            │            │
    │            │<─ 0 (중복) │
    │            │            │
    │<─ 400 Bad Request        │
    │  "이미 발급받은 쿠폰"       │
    │            │            │
```

**중요 포인트**: Kafka 메시지 발행 전에 Redis에서 차단되므로 불필요한 메시지 발행 방지

#### 예외 흐름 2: 재고 소진

```
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────┐
│ Consumer │  │Processor │  │    DB    │  │ Redis │
└────┬─────┘  └────┬─────┘  └────┬─────┘  └───┬───┘
     │             │             │            │
     │ processSingleIssue(userId, couponId)   │
     │────────────>│             │            │
     │             │             │            │
     │             │ SELECT remaining_quantity FROM coupons WHERE coupon_id = ?
     │             │────────────>│            │
     │             │             │            │
     │             │<─ 0 (재고없음) ───────────│
     │             │             │            │
     │             │ CouponSoldOutException   │
     │             │<────────────│            │
     │             │             │            │
     │             │ (Exception Catch - 재시도 안함)
     │             │ log.warn("재고 소진")     │
     │             │             │            │
     │<─ (정상처리)─│             │            │
     │             │             │            │
     │ commit offset             │            │
     │──────────────────────────────────>     │
     │             │             │            │
```

**중요 포인트**: 재고 소진 시 Redis Set은 유지하고 offset은 커밋하여 무한 재시도 방지

#### 예외 흐름 3: DB 장애 시 재시도

```
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────┐
│ Consumer │  │Processor │  │    DB    │  │ Redis │
└────┬─────┘  └────┬─────┘  └────┬─────┘  └───┬───┘
     │             │             │            │
     │ processSingleIssue(userId, couponId)   │
     │────────────>│             │            │
     │             │             │            │
     │             │ UPDATE coupons ...       │
     │             │────────────>│            │
     │             │             │            │
     │             │<─ SQLException ─────────│
     │             │             │            │
     │             │ SREM coupon:issued:{couponId} {userId}
     │             │─────────────────────────>│
     │             │             │            │
     │             │ throw Exception (재시도)  │
     │<─ Exception ─│             │            │
     │             │             │            │
     │ (offset 커밋 안함)          │            │
     │             │             │            │
     │ 다시 poll() 시 동일 메시지 재처리         │
     │             │             │            │
```

**중요 포인트**: 일시적 장애 시 Redis Set에서 제거 후 재시도 가능하도록 처리

---

## 5. 메시지 및 파티션 전략

### 5.1 Topic 설계

#### Topic 정보
```yaml
Topic Name: coupon-issue-request
Partitions: 3
Replication Factor: 1 (개발), 3 (운영)
Retention: 7 days
Compression: gzip
```

#### 파티션 수 선택 이유: 3개

| 고려사항 | 판단 근거 |
|---------|----------|
| **처리량** | 초당 100건 처리 목표 → 파티션당 33건 처리 |
| **확장성** | 향후 Consumer 3배 확장 가능 (3→9개) |
| **리소스** | 개발 환경 메모리 제약 고려 |
| **복잡도** | 파티션 수↑ = 관리 복잡도↑, 3개가 균형점 |

#### Replication Factor 선택

- **개발 환경**: 1 (단일 Broker)
- **운영 환경**: 3 (권장)
   - 이유: Broker 2대 장애 시에도 서비스 지속
   - 트레이드오프: 디스크 3배 사용, 네트워크 오버헤드

### 5.2 메시지 구조

```json
{
  "couponId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-12345",
  "requestedAt": "2025-12-18T10:30:00.123"
}
```

#### 필드 설명

| 필드 | 타입 | 설명 | 선택 이유 |
|-----|------|------|----------|
| `couponId` | String (UUID) | 쿠폰 ID | 파티션 키로 사용, 같은 쿠폰은 같은 파티션 |
| `userId` | String | 사용자 ID | 발급 대상 식별 |
| `requestedAt` | LocalDateTime | 요청 시각 | 디버깅, 모니터링용 (처리 지연 계산) |

#### 메시지 크기 최적화
- **예상 크기**: 약 150 bytes (JSON)
- **압축**: gzip 압축 시 약 80 bytes
- **판단**: 추가 메타데이터(주문 금액, 디바이스 정보 등) 불필요
   - 이유: 쿠폰 발급 시점에는 couponId, userId만 필요
   - 나머지 정보는 DB 조회로 획득 가능

### 5.3 파티션 전략 (핵심 설계)

#### 메시지 키 선택: `couponId`

**선택 이유**:
```java
// Kafka Partitioner 로직 (내부 동작)
partition = hash(messageKey) % partitionCount

// 예시
couponId = "coupon-A" → hash("coupon-A") % 3 = 0 → Partition 0
couponId = "coupon-A" → hash("coupon-A") % 3 = 0 → Partition 0 (동일!)
couponId = "coupon-B" → hash("coupon-B") % 3 = 1 → Partition 1
```

**효과**:
1. **순서 보장**: 같은 쿠폰(coupon-A)의 발급 요청은 항상 Partition 0으로 이동
2. **순차 처리**: Partition 0의 Consumer는 coupon-A 요청을 순차 처리
3. **병렬 처리**: coupon-A는 Partition 0, coupon-B는 Partition 1에서 동시 처리

#### 대안: userId를 키로 사용하지 않은 이유

| 비교 항목 | couponId (채택) | userId (기각) |
|----------|----------------|---------------|
| **순서 보장** | 같은 쿠폰 선착순 보장 | 같은 사용자 순서 보장 (불필요) |
| **동시성 제어** | 쿠폰별 재고 관리 용이 | 재고 관리 어려움 |
| **부하 분산** | 쿠폰 종류만큼 분산 | 사용자 수만큼 분산 (과도) |

#### 파티션 개수와 Consumer 개수의 관계

```
Scenario 1: Consumer 3개 = Partition 3개 (최적)
┌─────────────┬─────────────┬─────────────┐
│ Consumer 1  │ Consumer 2  │ Consumer 3  │
│    ↓        │    ↓        │    ↓        │
│ Partition 0 │ Partition 1 │ Partition 2 │
└─────────────┴─────────────┴─────────────┘
→ 모든 Consumer가 일함 (100% 활용)

Scenario 2: Consumer 6개 > Partition 3개 (비효율)
┌─────────────┬─────────────┬─────────────┐
│ Consumer 1  │ Consumer 3  │ Consumer 5  │
│    ↓        │    ↓        │    ↓        │
│ Partition 0 │ Partition 1 │ Partition 2 │
└─────────────┴─────────────┴─────────────┘
│ Consumer 2  │ Consumer 4  │ Consumer 6  │
│   (IDLE)    │   (IDLE)    │   (IDLE)    │
└─────────────┴─────────────┴─────────────┘
→ 3개 Consumer는 노는 상태 (50% 활용)

Scenario 3: Consumer 2개 < Partition 3개 (부하)
┌──────────────────┬──────────────────┐
│   Consumer 1     │   Consumer 2     │
│    ↓      ↓      │       ↓          │
│ Part 0  Part 1   │    Part 2        │
└──────────────────┴──────────────────┘
→ Consumer 1이 2배 일함 (불균형)
```

**결론**: 파티션 수 = Consumer 수 = 3개로 설정

---

## 6. 동시성 제어 전략

### 6.1 2단계 동시성 제어

#### Stage 1: Redis Set (빠른 중복 체크)

```
목적: API 응답 속도 유지 + 중복 요청 즉시 차단
시간: < 10ms

[사용자 1] → Redis SADD coupon:issued:A user1 → 1 (성공)
[사용자 1] → Redis SADD coupon:issued:A user1 → 0 (중복!)
[사용자 2] → Redis SADD coupon:issued:A user2 → 1 (성공)
```

**왜 Redis를 유지하는가?**
- Kafka만 사용 시: Consumer에서 중복 체크 → API 응답 시점에 중복 여부 모름
- Redis 사용 시: API 응답 전에 중복 확인 → 사용자 경험 개선

#### Stage 2: Kafka Partition (순서 보장)

```
목적: 같은 쿠폰의 발급 요청을 순차 처리
보장: Partition 내 메시지 순서 보장

Partition 0 (coupon-A 전용)
  ┌─────────────────────────────────┐
  │ msg1(user1) → msg2(user2) → ... │
  └─────────────────────────────────┘
         ↓ Consumer는 순차 처리
  user1 발급 완료 → user2 발급 완료
```

### 6.2 Database 레벨 동시성 제어 (최종 방어선)

```sql
-- CouponRepository.issueCoupon()
UPDATE coupons
SET issued_quantity = issued_quantity + 1,
    remaining_quantity = remaining_quantity - 1
WHERE coupon_id = :couponId
  AND remaining_quantity > 0;  -- 중요: 재고 체크
```

**3중 안전장치**:
1. **Redis Set**: 중복 방지 (1차)
2. **Kafka Partition**: 순서 보장 (2차)
3. **DB WHERE 조건**: 재고 체크 (3차, 최종 방어)

### 6.3 분산 락을 제거한 이유

#### 기존 방식 (IssueCouponUseCase)
```java
@DistributedLock(key = "'coupon:issue:'.concat(#couponId)")
public UserCouponResponse execute(String userId, String couponId) {
    // Lock 획득 대기 (최대 5초)
    // Lock 획득 후 처리
    // Lock 해제
}
```

**문제점**:
- 동시 요청 1000건 → 999건은 Lock 대기
- Lock 대기 시간 5초 * 1000건 = 약 5000초 (1.4시간)
- 처리량 제한: Lock 점유 시간에 비례

#### Kafka 방식 (Lock 불필요)
```java
public CouponQueueResponse execute(String userId, String couponId) {
    // Redis Set 중복 체크 (Lock 없음, 병렬 실행 가능)
    // Kafka 발행 (Lock 없음, 병렬 실행 가능)
    // 즉시 응답
}
```

**개선점**:
- 동시 요청 1000건 → 모두 즉시 처리 (< 1초)
- 처리량 제한 없음: Partition 수만큼 병렬 처리

---

## 7. 구현 세부사항

### 7.1 메시지 직렬화 (Serialization)

#### Spring Kafka 기본 설정 활용

```yaml
# application.yml
spring:
  kafka:
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
```

**선택 이유**:
- **JSON**: 사람이 읽을 수 있어 디버깅 용이
- **대안(Avro)**: 스키마 레지스트리 필요, 초기 단계에서는 과도

### 7.2 Consumer 설정

#### Concurrency 설정

```java
@KafkaListener(
    topics = "coupon-issue-request",
    groupId = "coupon-issue-group",
    concurrency = "3"  // 파티션 수와 동일
)
```

**동작 원리**:
- Spring Kafka가 내부적으로 3개 스레드 생성
- 각 스레드가 하나의 파티션 할당받음
- 스레드별 독립 실행 (병렬 처리)

#### Offset Commit 전략

**기본 설정**: Auto Commit (enable.auto.commit=true)

```
메시지 처리 성공 → Spring이 자동으로 Offset 커밋
메시지 처리 실패 (Exception) → Offset 커밋 안 함 → 재시도
```

**향후 개선 (Manual Commit)**:
```java
@KafkaListener(...)
public void consume(ConsumerRecord<String, CouponIssueMessage> record,
                    Acknowledgment ack) {
    try {
        // 처리
        ack.acknowledge();  // 명시적 커밋
    } catch (Exception e) {
        // 커밋 안 함 → 재시도
    }
}
```

### 7.3 예외 처리 전략

#### CouponIssueProcessor의 예외 처리

```java
@Transactional
public void processSingleIssue(String userId, String couponId) {
    try {
        couponIssueService.issue(userId, couponId);
    } catch (CouponSoldOutException e) {
        // 재고 소진 - Set에 유지 (재시도 불가)
        log.warn("쿠폰 재고 소진");
    } catch (DuplicateCouponIssueException e) {
        // 중복 발급 - 정상 케이스
        log.debug("이미 발급됨");
    } catch (Exception e) {
        // 기타 예외 - Set에서 제거 (재시도 가능)
        queueService.removeFromIssuedSet(couponId, userId);
        throw e;  // Kafka 재시도 트리거
    }
}
```

**재시도 로직**:
- 재시도 가능 예외(DB 장애 등): throw → Kafka 재시도
- 재시도 불가 예외(재고 소진): catch → 로그만 기록

### 7.4 Redis Set TTL 설정 (향후 추가)

```java
// 발급 Set에 TTL 설정 (예: 7일)
redisTemplate.expire(issuedSetKey, 7, TimeUnit.DAYS);
```

**이유**:
- Redis 메모리 무한 증가 방지
- 쿠폰 만료 후 7일 뒤 자동 삭제

---

## 8. 예상 효과 및 성능 개선

### 8.1 Redis Queue vs Kafka 상세 비교

#### 아키텍처 비교

| 구분 | Redis Queue | Kafka |
|------|------------|-------|
| **메시지 저장소** | In-Memory (RAM) | Disk + Page Cache |
| **메시지 보관** | 처리 후 즉시 삭제 (BRPOP) | Retention 기간 동안 보관 (7일) |
| **병렬 처리** | 단일 Queue → 경합 발생 | Partition 기반 → 독립 처리 |
| **Consumer 확장** | Worker 수↑ → 경합↑ | Consumer Group → 파티션당 1개 할당 |
| **장애 복구** | 유실된 메시지 복구 불가 | Offset 기반 재처리 가능 |

#### 성능 벤치마크 (이론값)

**테스트 시나리오**: 1,000명이 동시에 쿠폰 발급 요청

| 단계 | Redis Queue | Kafka | 비고 |
|------|------------|-------|------|
| **1. API 요청 처리** | | | |
| - Redis SADD (중복 체크) | 1ms | 1ms | 동일 (Redis 사용) |
| - 메시지 발행 | 5ms (LPUSH) | 10ms (Kafka send) | Kafka가 약간 느림 |
| - API 응답 시간 | **~6ms** | **~11ms** | Kafka가 5ms 느림 |
| | | | |
| **2. 백엔드 처리 (1,000건)** | | | |
| - Worker/Consumer 수 | 3개 (경합 발생) | 3개 (파티션별 독립) | |
| - 초당 처리량 (TPS) | ~33 TPS | ~100 TPS | **Kafka 3배 빠름** |
| - 전체 처리 시간 | ~30초 | ~10초 | **Kafka 3배 빠름** |
| | | | |
| **3. 장애 상황** | | | |
| - Redis/Kafka 재시작 시 | 큐 전체 유실 | 메시지 보존 | **Kafka 안정적** |
| - Consumer 재시작 시 | 처리중 메시지 유실 | Offset 기반 재처리 | **Kafka 안정적** |
| - DB 장애 복구 후 | 수동 재발급 필요 | 자동 재시도 | **Kafka 자동화** |

#### 처리량 분석

**Redis Queue 방식**:
```
┌─────────┐
│ Queue   │ ← 모든 Worker가 동일 큐에서 BRPOP
└────┬────┘
     ├───> Worker 1 (처리 중...)
     ├───> Worker 2 (처리 중...)
     └───> Worker 3 (처리 중...)

문제점:
- Worker들이 Lock을 두고 경합
- 하나의 Worker가 느리면 전체 처리 속도 저하
- Worker 추가해도 성능 향상 제한적
```

**Kafka 방식**:
```
┌──────────────┐
│ Partition 0  │ ──> Consumer Thread 1 (독립 처리)
└──────────────┘
┌──────────────┐
│ Partition 1  │ ──> Consumer Thread 2 (독립 처리)
└──────────────┘
┌──────────────┐
│ Partition 2  │ ──> Consumer Thread 3 (독립 처리)
└──────────────┘

장점:
- 각 파티션이 독립적으로 처리
- 하나의 Consumer가 느려도 다른 파티션 영향 없음
- Consumer Group 추가로 수평 확장 가능
```

#### 메시지 유실 비교

**Redis Queue 유실 시나리오**:

| 시나리오 | 유실 가능성 | 영향도 |
|---------|-----------|--------|
| Redis 서버 재시작 | ✅ **높음** | 큐 전체 유실 |
| Worker 프로세스 Crash | ✅ **높음** | 처리 중인 메시지 유실 |
| 네트워크 순간 단절 | ✅ **중간** | BRPOP 중 유실 가능 |
| 메모리 부족 (OOM) | ✅ **높음** | 큐 전체 유실 |

**Kafka 유실 시나리오**:

| 시나리오 | 유실 가능성 | 영향도 |
|---------|-----------|--------|
| Kafka 서버 재시작 | ❌ **없음** | 디스크에 보존 |
| Consumer 프로세스 Crash | ❌ **없음** | Offset 기반 재처리 |
| 네트워크 순단 | ❌ **없음** | 재연결 후 이어서 처리 |
| 디스크 장애 (Replication) | ❌ **없음** | Replica에서 복구 |

### 8.2 정량적 개선 효과

| 지표 | Redis Queue | Kafka | 개선율 |
|------|------------|-------|--------|
| **API 응답 시간** | ~6ms | ~11ms | **5ms 증가** (허용 범위) |
| **처리량 (TPS)** | 약 33 TPS (단일 Worker) | 약 100 TPS (3 Partition) | **3배↑** |
| **메시지 유실률** | 높음 (Redis 장애 시) | 낮음 (디스크 영속화) | **리스크 제거** |
| **확장성** | 제한적 (Worker 경합) | 높음 (Consumer Group) | **무한 확장** |
| **장애 복구 시간** | 수동 복구 (재발급) | 자동 재처리 | **자동화** |

### 8.3 정성적 개선 효과

#### 운영 안정성
- **Before**: Redis 재시작 시 대기 큐 소실 → 사용자 불만
- **After**: Kafka 재시작해도 메시지 보존 → 이어서 처리

#### 모니터링
- **Before**: Redis CLI로 큐 사이즈 확인
- **After**: Kafka Manager로 파티션별 Lag, Consumer 처리 속도 실시간 확인

#### 개발 생산성
- **Before**: Worker 로직 수정 시 서비스 중단 필요
- **After**: Consumer를 Rolling Restart로 무중단 배포 가능

### 8.4 트레이드오프 (단점 인정)

| 항목 | 단점 | 완화 방안 |
|------|------|----------|
| **운영 복잡도** | Kafka Cluster 관리 필요 | Docker Compose로 로컬 환경 간소화 |
| **초기 학습 비용** | Kafka 개념 이해 필요 | 문서화 + 팀 교육 |
| **인프라 비용** | Broker 3대 운영 시 비용↑ | 개발 환경은 단일 Broker 사용 |

---

## 9. 향후 확장 방안

### 9.1 Phase 2: Dead Letter Queue (DLQ)

**목적**: 반복 실패 메시지를 별도 Topic으로 이동

```
coupon-issue-request (Main Topic)
          ↓
   처리 실패 (3회 재시도)
          ↓
coupon-issue-dlq (Dead Letter Queue)
          ↓
   수동 확인 및 처리
```

**구현**:
```java
@KafkaListener(topics = "coupon-issue-request")
public void consume(CouponIssueMessage msg) {
    try {
        process(msg);
    } catch (Exception e) {
        if (retryCount >= 3) {
            kafkaTemplate.send("coupon-issue-dlq", msg);
        } else {
            throw e;  // 재시도
        }
    }
}
```

### 9.2 Phase 3: Consumer 동적 확장

**목적**: 트래픽 증가 시 Consumer 자동 확장

```yaml
# Kubernetes HPA (Horizontal Pod Autoscaler)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: coupon-consumer
spec:
  scaleTargetRef:
    name: coupon-consumer
  minReplicas: 3
  maxReplicas: 9  # 파티션 수의 3배
  metrics:
  - type: Pods
    pods:
      metric:
        name: kafka_consumer_lag
      target:
        type: AverageValue
        averageValue: "1000"  # Lag 1000 이상 시 스케일 아웃
```

### 9.3 Phase 4: 멀티 리전 복제

**목적**: 글로벌 서비스 대비 리전 간 복제

```
Seoul Kafka Cluster
       ↓ (Mirror Maker)
Tokyo Kafka Cluster
```

### 9.4 Phase 5: 이벤트 소싱 (Event Sourcing)

**목적**: 쿠폰 발급 이력을 이벤트 스트림으로 관리

```
이벤트: CouponIssued, CouponUsed, CouponExpired
→ Kafka Streams로 집계
→ 실시간 대시보드 (발급 수, 사용률 등)
```

---

## 10. 결론

### 10.1 핵심 의사결정 요약

| 결정 사항 | 선택 | 이유 |
|----------|------|------|
| **메시지 큐** | Kafka | 영속성, 확장성, 모니터링 |
| **파티션 수** | 3개 | 처리량 3배, 확장성 확보 |
| **메시지 키** | couponId | 같은 쿠폰 순차 처리 보장 |
| **중복 체크** | Redis Set 유지 | 빠른 API 응답 유지 |
| **전환 방식** | 완전 교체 | 코드 단순화, 명확한 책임 |

### 10.2 기대 효과

1. **처리량 3배 향상**: 33 TPS → 100 TPS
2. **메시지 유실 제거**: 디스크 영속화
3. **무중단 확장**: Consumer Group 추가
4. **운영 자동화**: Offset 기반 재처리

### 10.3 성공 지표 (KPI)

- [ ] API 응답 시간 < 50ms 유지
- [ ] 처리량 100 TPS 이상 달성
- [ ] 메시지 유실률 0%
- [ ] Consumer Lag < 1000 메시지 유지

---

**작성일**: 2025-12-18
**작성자**: Ecommerce Core Team
**문서 버전**: 1.0
**다음 리뷰**: Phase 2 구현 후 (DLQ 추가 시)
