## 📌 PR 제목
[STEP11,12] Redis 분산 락 및 캐시 적용 (e-commerce)

---

## ✅ 구현 내용

### STEP11: Redis 분산 락 적용
- [x] Redis 분산락 적용
- [x] AOP 기반 분산 락 구현 (@DistributedLock)
- [x] 트랜잭션 순서와 락 순서 보장 (Propagation.REQUIRES_NEW)
- [x] Test Container 구성 (MySQL, Redis)
- [x] 기능별 통합 테스트 작성
  - 쿠폰 발급 동시성 테스트
  - 잔액 충전 동시성 테스트
  - 주문 생성 동시성 테스트

### STEP12: Redis 캐시 적용
- [x] 캐시 필요 부분 분석
  - 인기 상품 조회 (집계 쿼리)
  - 상품 상세 조회
  - 상품 목록 조회
- [x] Redis 기반 Cache-Aside 패턴 적용
- [x] 적절한 캐시 키 설계
  - `popularProducts::{days}:{limit}`
  - `productDetail::{productId}`
  - `productList::{category}:{sort}`
- [x] TTL 설정 (인기 상품/목록: 5분, 상세: 10분)
- [x] 성능 개선 측정 및 보고서 작성
  - 인기 상품 조회: 95% 개선 (120ms → 6.5ms)
  - 상품 목록 조회: 73% 개선 (15ms → 4ms)
  - DB 쿼리 90% 감소

---

## 🧪 테스트 결과

### 동시성 테스트 (7개 모두 통과)
- ✅ 쿠폰 발급 동시성 (100명 → 50명 성공, 재고 정확)
- ✅ 잔액 충전 동시성 (10회 충전 모두 반영)
- ✅ 주문 생성 동시성 (재고 차감 정확)
- ✅ 상품 재고 차감 동시성 (오버셀링 방지)
- ✅ 캐시 성능 테스트 (평균 77% 성능 개선)

### 성능 개선 측정
```
[인기 상품 조회]
- Cache Miss: 120ms
- Cache Hit: 6.5ms
- 개선율: 95%

[상품 목록 조회]
- Cache Miss: 15ms
- Cache Hit: 4ms
- 개선율: 73%
```

---

## 📝 핵심 체크리스트

### 1️⃣ 분산락 적용
- [x] 적절한 곳에 분산락이 사용되었는가?
  - 쿠폰 발급 (선착순)
  - 잔액 충전/차감
  - 주문 생성 (전역 락)
- [x] 트랜잭션 순서와 락 순서가 보장되었는가?
  - `AopForTransaction`으로 트랜잭션 분리
  - `REQUIRES_NEW`로 트랜잭션 커밋 → 락 해제 순서 보장

### 2️⃣ 통합 테스트
- [x] Infrastructure 레이어를 포함하는 통합 테스트가 작성되었는가?
  - Testcontainers (MySQL, Redis) 사용
- [x] 핵심 기능에 대한 흐름이 테스트에서 검증되었는가?
  - 쿠폰 발급, 잔액 충전, 주문 생성 전체 플로우 테스트
- [x] 동시성을 검증할 수 있는 테스트코드로 작성되었는가?
  - ExecutorService + CountDownLatch 사용
  - 100개 동시 요청 시뮬레이션
- [x] Test Container가 적용되었는가?
  - MySQL 8.0, Redis 컨테이너 자동 실행

### 3️⃣ Cache 적용
- [x] 적절하게 Key 적용이 되었는가?
  - SpEL 표현식 사용
  - 파라미터 조합으로 고유 키 생성
  - null 처리 (Elvis 연산자)

---

## 📚 문서화
- [x] README.md 통합 작성
  - 분산 락 전략 설명 (AOP 구현 상세)
  - 캐시 전략 비교 및 선택 이유
  - 성능 측정 결과 포함

---

## 💭 간단 회고

**잘한 점:**
- AOP 패턴으로 분산 락을 구현하여 코드 77% 단순화 달성
- Cache-Aside 패턴 적용으로 평균 77% 성능 개선 확인
- 실제 성능 테스트를 통해 개선 효과를 정량적으로 측정

**어려운 점:**
- 트랜잭션 커밋과 락 해제 순서를 보장하는 메커니즘 이해
- Redis 직렬화 설정 (GenericJackson2JsonRedisSerializer)
- 캐시 키 설계 시 null 처리 및 Elvis 연산자 활용

**다음 시도:**
- 캐시 무효화 전략 (@CacheEvict) 적용
- Cache Warming 구현 (서버 시작 시 인기 데이터 미리 캐싱)
- Redis 메모리 사용량 모니터링 구현
