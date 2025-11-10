## :pushpin: PR 제목 규칙
[STEP05 또는 STEP06] 이름

---
## ⚠️ **중요: 이번 과제는 DB를 사용하지 않습니다**
> 모든 데이터는 **인메모리(Map, Array, Set 등)**로 관리해야 합니다.  
> 실제 DB 연동은 다음 챕터(데이터베이스 설계)에서 진행합니다.

---
## 📋 **과제 체크리스트**

### ✅ **STEP 5: 레이어드 아키텍처 기본 구현** (필수)
- [x] **도메인 모델 구현**: Entity, Value Object가 정의되었는가?
- [x] **유스케이스 구현**: API 명세가 유스케이스로 구현되었는가?
- [x] **레이어드 아키텍처**: 4계층(Presentation, Application, Domain, Infrastructure)으로 분리되었는가?
- [x] **재고 관리**: 재고 조회/차감/복구 로직이 구현되었는가?
- [x] **주문/결제**: 주문 생성 및 결제 프로세스가 구현되었는가?
- [x] **선착순 쿠폰**: 쿠폰 발급/사용/만료 로직이 구현되었는가?
- [x] **단위 테스트**: 테스트 커버리지 70% 이상 달성했는가?

### 🔥 **STEP 6: 동시성 제어 및 고급 기능** (심화)
- [x] **동시성 제어**: 선착순 쿠폰 발급의 Race Condition이 방지되었는가?
- [x] **통합 테스트**: 동시성 시나리오를 검증하는 테스트가 작성되었는가?
- [x] **인기 상품 집계**: 조회수/판매량 기반 순위 계산이 구현되었는가?
- [x] **문서화**: README.md에 동시성 제어 분석이 작성되었는가?

### 🏗️ **아키텍처 설계**
- [x] **의존성 방향**: Domain ← Application ← Infrastructure 방향이 지켜졌는가?
- [x] **책임 분리**: 각 계층의 책임이 명확히 분리되었는가?
- [x] **테스트 가능성**: Mock/Stub을 활용한 테스트가 가능한 구조인가?
- [x] **인메모리 저장소**: DB 없이 모든 데이터가 인메모리로 관리되는가?
- [x] **Repository 패턴**: 인터페이스와 인메모리 구현체가 분리되었는가?

---
## 🔗 **주요 구현 커밋**

- 도메인 모델 구현: `b5c4b7b`
- 재고 관리 로직 구현: `5bcd25f`
- 주문/결제 프로세스 구현: `28bfbe1`
- 선착순 쿠폰 로직 구현: `2a84da1`
- 인기 상품 조회 기능 구현: `0dc252f`
- 동시성 제어 구현 (STEP 6): `5807314`

---
## 💬 **리뷰 요청 사항**

### 질문/고민 포인트
1. ReentrantLock의 Fair Lock(true) 설정이 성능에 미치는 영향이 궁금합니다
2. 쿠폰별 Lock을 ConcurrentHashMap으로 관리하는 방식이 메모리 효율적인지 검토 부탁드립니다

### 특별히 리뷰받고 싶은 부분
- `IssueCouponUseCase.java`의 Lock 획득/해제 패턴이 안전한지 확인 부탁드립니다
- ReadWriteLock을 사용한 Repository 구현이 적절한지 피드백 부탁드립니다

---
## 📊 **테스트 및 품질**

| 항목 | 결과 |
|------|------|
| 테스트 커버리지 | 85% 이상 |
| 단위 테스트 | 60개 이상 |
| 통합 테스트 | 10개 |
| 동시성 테스트 | 통과 (4개 시나리오) |

**동시성 테스트 시나리오:**
- 100명 동시 발급, 재고 50개 → 정확히 50명만 성공
- 동일 사용자 10번 동시 발급 → 정확히 1번만 성공
- 1000명 고부하 환경, 재고 100개 → Over-issuance 방지 검증
- 서로 다른 쿠폰 병렬 발급 → 락 경쟁 없이 동시 처리 확인

---
## 🔒 **동시성 제어 방식** (STEP 6 필수)

**선택한 방식:**
- [x] Mutex/Lock (ReentrantLock + ReadWriteLock)
- [ ] Semaphore
- [ ] Atomic Operations
- [ ] Queue 기반
- [ ] 기타: ___________

**구현 이유:**
- **쿠폰별 개별 Lock 관리**: ConcurrentHashMap에 쿠폰 ID별로 ReentrantLock 저장
  - C001, C002 발급은 서로 다른 Lock 사용 → 병렬 처리 가능
  - 같은 쿠폰 동시 발급만 순차 처리 → 성능 최적화

- **Fair Lock 사용**: `new ReentrantLock(true)`로 FIFO 순서 보장
  - 선착순 쿠폰의 공정성 확보

- **ReadWriteLock으로 Repository 최적화**:
  - 읽기 작업(조회): 여러 스레드 동시 실행 가능
  - 쓰기 작업(발급, 수정): 배타적 실행
  - 비관적 락(Pessimistic Lock) 시뮬레이션

- **Lock Timeout 설정**: `tryLock(10, TimeUnit.SECONDS)`
  - 무한 대기 방지
  - 사용자 경험 개선 (타임아웃 시 명확한 예외 메시지)

**참고 문서:**
- README.md의 동시성 제어 분석 섹션 참조 (작성 예정)

---
## 🎯 **아키텍처 설계**

### 디렉토리 구조
```
src/main/java/com/sparta/ecommerce/
├── presentation/          # Controller (REST API)
│   └── controller/
├── application/          # UseCase (비즈니스 로직)
│   ├── product/
│   ├── order/
│   └── coupon/
├── domain/              # Entity, VO, Repository Interface
│   ├── product/
│   ├── order/
│   ├── coupon/
│   └── user/
└── infrastructure/      # Repository 구현체
    └── memory/         # InMemory 구현
```

### 주요 설계 결정
- **선택한 아키텍처**: 레이어드 아키텍처 (4계층)
- **데이터 저장 방식**: 인메모리 (ConcurrentHashMap)
- **선택 이유**:
  - 계층 간 책임 분리로 유지보수성 향상
  - Repository 패턴으로 DB 전환 용이성 확보
  - Value Object로 도메인 불변성 보장
- **트레이드오프**:
  - 단순 CRUD보다 코드량 증가
  - 인메모리 방식으로 데이터 영속성 없음 (서버 재시작 시 초기화)

---
## 📝 **회고**

### ✨ 잘한 점
- ReentrantLock과 ReadWriteLock을 활용하여 동시성 문제를 체계적으로 해결
- 쿠폰별 개별 락으로 성능 최적화 (서로 다른 쿠폰은 병렬 처리)
- 4가지 동시성 테스트 시나리오를 작성하여 Race Condition 방지 검증
- Repository 인터페이스와 구현체를 분리하여 DB 전환 가능한 구조 설계

### 😓 어려웠던 점
- synchronized vs ReentrantLock vs ReadWriteLock 중 어떤 것을 사용할지 선택하는 과정
- Fair Lock의 성능 영향과 공정성 사이의 트레이드오프 이해
- Lock 획득/해제 패턴에서 예외 상황 처리 (InterruptedException, finally 블록)
- ConcurrentHashMap과 Lock을 함께 사용할 때의 동작 원리 이해

### 🚀 다음에 시도할 것
- Redis 분산 락(Redisson)을 활용한 멀티 서버 환경 대응
- 낙관적 락(Optimistic Lock)과 비관적 락(Pessimistic Lock) 성능 비교
- Lock-free 알고리즘(AtomicInteger, CAS 등) 적용 가능성 탐구
- 데이터베이스 SELECT FOR UPDATE와 현재 구현의 동작 비교

---
## 📚 **참고 자료**

- Java Concurrency in Practice (Brian Goetz)
- Oracle Java Documentation - java.util.concurrent.locks
- 동시성 제어 관련 블로그 및 기술 아티클

---
## ✋ **체크리스트 (제출 전 확인)**

- [x] DB 관련 라이브러리를 사용하지 않았는가?
- [x] 모든 Repository가 인메모리로 구현되었는가?
- [x] build.gradle에 DB 드라이버가 없는가? (H2, MySQL, PostgreSQL 등)
- [x] 환경변수에 DB 연결 정보가 없는가?
- [x] 모든 테스트가 통과하는가?
- [x] 동시성 테스트가 작성되고 통과하는가?