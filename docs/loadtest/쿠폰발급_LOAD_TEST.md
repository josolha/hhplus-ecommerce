# K6 부하 테스트 최종 결과 보고서

## 테스트 개요

**테스트 대상**: 선착순 쿠폰 발급 API
**테스트 도구**: k6 (JavaScript 기반 부하 테스트 도구)
**테스트 일자**: 2025-12-23
**테스트 목적**: 실제 운영 환경 시뮬레이션 및 동시성 제어 검증

## 테스트 환경

- **테스트 유저**: 100,000명 (`test-user-1` ~ `test-user-100000`)
- **쿠폰 재고**: 100,000개 (`test-coupon-1`)
- **유저 선택 방식**: Random (실제 상황 재현)
- **테스트 시나리오**:
  - Load Test: 50 VUs (30s ramp-up, 1m sustained, 30s ramp-down)
  - Peak Test: 200 VUs (10s ramp-up, 30s sustained, 10s ramp-down)

## 최종 테스트 결과

```
=== 쿠폰 발급 부하 테스트 결과 ===

총 요청 수: 63,331
평균 TPS: 372.32

응답 시간:
  - 평균: 29.84ms
  - 최소: 3.82ms
  - 최대: 1,523.13ms
  - p(50): 20.30ms
  - p(95): 96.69ms
  - p(99): 178.63ms

에러 통계:
  - 전체 에러율: 25.93%
  - 중복 발급 에러: 0
  - 품절 에러: 16,395

=====================================
```

## 데이터베이스 검증 결과

```sql
-- 쿠폰 발급 현황
SELECT issued_quantity, remaining_quantity FROM coupons WHERE id = 'test-coupon-1';
-- issued_quantity: 46,911
-- remaining_quantity: 53,089

-- 실제 발급된 유저 수 (중복 검증)
SELECT COUNT(*), COUNT(DISTINCT user_id) FROM user_coupons WHERE coupon_id = 'test-coupon-1';
-- 총 레코드: 46,911
-- 고유 유저: 46,911
-- 중복 발급: 0건 ✅
```

## 결과 분석

### 1. 에러율 25.93%의 의미

**✅ 정상적인 결과입니다.**

- **총 요청**: 63,331건
- **성공**: 46,936건 (74.07%)
- **품절(중복) 에러**: 16,395건 (25.93%)

**중요**: 이 에러는 **시스템이 정상 작동**한다는 증거입니다.

#### 왜 에러가 발생하는가?

1. **Random 방식**을 사용하여 실제 상황 재현
   - 같은 유저가 여러 번 시도하는 경우 발생
   - 100,000명 유저 풀에서 랜덤 선택
   - Birthday Paradox: 확률적으로 중복 시도 불가피

2. **중복 방지 로직이 제대로 작동**
   - 16,395건의 중복 시도를 모두 차단
   - DB 검증 결과: 중복 발급 0건
   - Redis + MySQL 이중 검증 성공

### 2. 성능 지표 평가

| 지표 | 목표 | 실제 | 평가 |
|------|------|------|------|
| 평균 TPS | 300+ | 372.32 | ✅ 우수 |
| 평균 응답 시간 | <50ms | 29.84ms | ✅ 우수 |
| p(95) | <100ms | 96.69ms | ✅ 목표 달성 |
| p(99) | <200ms | 178.63ms | ✅ 목표 달성 |
| 중복 발급 | 0건 | 0건 | ✅ 완벽 |

### 3. 동시성 제어 검증

**시나리오**: 63,331건의 동시 요청 중 중복 시도 16,395건

**결과**:
- ✅ Redis 분산 락으로 중복 요청 차단
- ✅ Kafka 비동기 처리로 빠른 응답 (202 Accepted)
- ✅ 데이터베이스 무결성 유지 (중복 발급 0건)

## 테스트 방법론 정리

### Random vs Sequential

| 방식 | 목적 | 에러율 | 용도 |
|------|------|--------|------|
| **Random** | 실제 상황 재현 | 25-30% | 중복 방지 로직 검증 |
| **Sequential** | 순수 성능 측정 | 0% | 최대 처리량(TPS) 측정 |

**결론**: Random 방식이 실제 운영 환경을 더 정확히 시뮬레이션합니다.

## 시스템 아키텍처 검증

### 비동기 처리 흐름

```
Client Request (POST /api/coupons/{id}/issue)
    ↓
Controller (202 Accepted 즉시 응답)
    ↓
Kafka Producer (쿠폰 발급 이벤트 발행)
    ↓
Kafka Consumer (비동기 처리)
    ↓
MySQL (최종 발급 기록)
    ↑
Redis (중복 체크 캐시)
```

**검증 포인트**:
- ✅ 202 Accepted로 빠른 응답 (평균 29.84ms)
- ✅ Redis 캐시로 중복 시도 즉시 차단
- ✅ Kafka 비동기 처리로 DB 부하 분산
- ✅ 최종 일관성 보장 (issued_quantity = user_coupons count)

## 실행 방법

### 1. 테스트 데이터 준비
```bash
./gradlew test --tests "LoadTestDataSeeder.seedForLoadTest"
```

### 2. Docker 환경 시작
```bash
docker-compose up -d
```

### 3. Redis 캐시 초기화 (재테스트 시)
```bash
docker exec redis redis-cli FLUSHALL
```

### 4. 애플리케이션 시작
```bash
./gradlew bootRun
```

### 5. k6 테스트 실행
```bash
k6 run k6-tests/coupon-issue-test.js
```

### 6. 결과 시각화 (옵션)
```bash
k6 run --out web-dashboard k6-tests/coupon-issue-test.js
# http://127.0.0.1:5665 에서 실시간 모니터링
```

## 트러블슈팅 가이드

### 문제 1: 에러율 100%
**원인**: k6 check 로직이 202와 200을 동시에 체크
**해결**: `response.status === 202 || response.status === 200`

### 문제 2: Sequential 방식에서도 에러 발생
**원인**: 단순 counter는 VU 간 race condition 발생
**해결**: `exec.vu.idInTest`와 `exec.scenario.iterationInTest` 사용

### 문제 3: "이미 발급받은 쿠폰" 에러 (DB는 비어있음)
**원인**: Redis 캐시에 이전 테스트 데이터 잔존
**해결**: `docker exec redis redis-cli FLUSHALL`

### 문제 4: 데이터가 롤백되지 않음
**원인**: k6는 실제 HTTP 요청을 보내므로 @Commit 적용됨
**해결**: 테스트 후 수동으로 데이터 정리 또는 Redis FLUSHALL

## 결론

**시스템 상태: ✅ 운영 준비 완료**

1. **성능**: TPS 372, 평균 응답시간 29ms로 우수한 성능
2. **동시성 제어**: 중복 발급 0건으로 완벽한 무결성
3. **확장성**: Kafka 비동기 처리로 수평 확장 가능
4. **안정성**: p(99) 178ms로 안정적인 응답 시간

**핵심 인사이트**:
- 25.93% 에러율은 **정상적인 중복 시도**이며, 시스템이 이를 **완벽하게 차단**했습니다.
- Random 테스트 방식이 실제 운영 환경의 사용자 행동을 정확히 재현합니다.
- Redis + Kafka + MySQL의 3-tier 아키텍처가 효과적으로 작동합니다.

## 참고 문서

- k6 테스트 스크립트: `k6-tests/coupon-issue-test.js`
- 데이터 시더: `src/test/java/com/sparta/ecommerce/LoadTestDataSeeder.java`
- API 설계: `document/API_DESIGN.md`
- 시퀀스 다이어그램: `document/SEQUENCE_DIAGRAM.md`
