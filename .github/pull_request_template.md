## :pushpin: PR 제목 규칙
[STEP13-14] Redis 기반 랭킹 및 비동기 쿠폰 발급

---
## :memo: 작업 내용

### STEP 13: Redis Sorted Set 실시간 랭킹
- [x] Redis Sorted Set으로 실시간 인기 상품 랭킹 구현
- [x] DB 집계 쿼리 제거 (17.5초 → 0.7초, 96% 개선)
- [x] 주문 시 ZINCRBY로 즉시 랭킹 업데이트
- [x] ZUNIONSTORE + ZREVRANGE로 최근 N일 Top 상품 조회
- [x] Redis 장애 시 DB Fallback 구현

**성능 비교 테스트:**
- DB 집계: 17,520ms
- Spring Cache: 6.5ms (실시간 X)
- Redis 랭킹: 697ms (실시간 O) ✅ 선택

---

### STEP 14: Redis Blocking Queue 비동기 쿠폰 발급
- [x] 기존 **분산 락 방식 제거** (순서 보장 안됨 + 느림)
- [x] Redis BRPOP 기반 비동기 큐 구현
- [x] 응답 속도 99% 개선 (5~10초 → 50ms)
- [x] FIFO 순서 보장 추가
- [x] Worker 스레드 3개로 백그라운드 처리
- [x] Redis Set으로 중복 발급 방지

**기술 선택 과정:**
- Polling: CPU 낭비 ❌
- Pub/Sub: 메시지 유실 위험 ❌
- **BRPOP: FIFO + 메시지 안정성** ✅ 선택

---

## :white_check_mark: 핵심 체크리스트

### 1. Ranking Design
- [x] Redis Sorted Set 선택 이유를 명확히 설명
- [x] DB 집계 / Spring Cache / Redis 랭킹 비교
- [x] 실시간성 우선 순위 고려
- [x] 성능 측정 데이터 포함 (PERFORMANCE_COMPARISON.md)

### 2. Asynchronous Design
- [x] **분산 락 제거** 및 Redis Queue로 전환
- [x] Polling / Pub/Sub / BRPOP 비교
- [x] FIFO 순서 보장 구현
- [x] Worker 패턴으로 비동기 처리
- [x] 메시지 안정성 확보 (Redis 영속성)

### 3. 통합 테스트
- [x] Testcontainers로 Redis + MySQL 독립 테스트 환경
- [x] 8개 동시성 테스트 모두 통과
  - BlockingQueueCouponTest (5개)
  - CouponQueueConcurrencyTest (3개)
- [x] 재고 정확성 100% 검증
- [x] FIFO 순서 보장 검증

---

## :chart_with_upwards_trend: 성능 개선 결과

| 기능 | 기존 | 개선 | 개선율 |
|------|------|------|--------|
| 인기 상품 조회 | 17,520ms | 697ms | 96% |
| 쿠폰 발급 응답 | 5~10초 | 50ms | 99% |
| DB 부하 | 매우 높음 | 낮음 | 95% |
| 처리 스레드 | 1000개 | 3개 | 99.7% |
| **순서 보장** | ❌ | ✅ | - |

---

## :bulb: 회고

### 잘한 점
- 각 기술(DB 집계, Cache, Sorted Set / Polling, Pub/Sub, BRPOP)을 직접 비교하여 장단점 파악
- 분산 락의 한계(순서 보장 안됨)를 명확히 인식하고 Redis Queue로 전환
- 8개 테스트로 동시성, 재고 정확성, FIFO 순서 모두 검증

### 어려웠던 점
- **Redis Blocking Queue(BRPOP) 개념이 생소함**
  - Blocking 방식이 어떻게 동작하는지 처음엔 이해가 어려웠음
  - Worker 스레드가 큐를 어떻게 계속 모니터링하는지 감이 안 잡힘
  - Pub/Sub과의 차이(메시지 유실 vs 영속성)를 실제로 겪어봐야 체감될 것 같음
- CouponStock을 record에서 class로 변경하는 과정에서 JPA 호환성 이슈
- 테스트에서 UUID 자동 생성과 수동 ID 할당 충돌 문제

### 다음 시도
- **Blocking Queue 패턴을 더 깊이 학습**하여 실무에서 활용할 수 있도록
- WebSocket/SSE로 쿠폰 발급 완료 알림 기능 추가
- Redis Cluster 구성하여 고가용성 확보
- 상품 정보 캐싱 추가로 697ms → 170ms 추가 개선

---

## :page_facing_up: 관련 문서
- [README.md](../README.md) - 전체 기술 보고서
- https://josolha.tistory.com/75 -Redis 직렬화 문제 해결 
---

## :rocket: 테스트 실행 방법

```bash
# 전체 테스트
./gradlew test

# 랭킹 성능 테스트
./gradlew test --tests RankingPerformanceComparisonTest

# 쿠폰 동시성 테스트
./gradlew test --tests BlockingQueueCouponTest
./gradlew test --tests CouponQueueConcurrencyTest
```
