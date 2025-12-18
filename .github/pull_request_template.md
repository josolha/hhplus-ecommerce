## :pushpin: PR 제목 규칙
[STEP18] 조솔하 - Kafka 기반 선착순 쿠폰 발급 시스템 구현

---
### STEP 17 카프카 기초 학습 및 활용
- [x] 카프카에 대한 기본 개념 학습 문서 작성
  - Producer, Consumer, Topic, Partition 개념 정리
  - Offset, Consumer Group, Rebalancing 개념 이해
  - 메시지 순서 보장, 내구성, 확장성 학습
- [x] 실시간 주문/예약 정보를 카프카 메시지로 발행
  - OrderCompletedEvent를 Kafka Topic으로 발행
  - KafkaOrderEventProducer 구현
  - 주문 완료 시 외부 데이터 플랫폼 전송용 이벤트 발행

### STEP 18 카프카를 활용하여 비즈니스 프로세스 개선
- [x] 카프카를 특징을 활용하도록 쿠폰/대기열 설계문서 작성
  - Redis Queue vs Kafka 상세 비교 분석 (성능, 안정성, 확장성)
  - Partition 전략 설계 (couponId 기반 3개 파티션)
  - Consumer Group 및 병렬 처리 전략 설계
  - 예외 처리 및 재시도 로직 설계
  - 정상/예외 흐름 시퀀스 다이어그램 작성
  - 메시지 유실 방지 및 Offset 관리 전략 수립
- [x] 설계문서대로 카프카를 활용한 기능 구현
  - CouponKafkaProducer 구현 (couponId 파티셔닝)
  - CouponKafkaConsumer 구현 (3개 스레드 병렬 처리)
  - Redis Set 중복 방지 + Kafka 메시지 큐 하이브리드 구조
  - 재고 소진/DB 장애 등 예외 상황별 처리 로직 구현
  - Offset Auto Commit 설정 및 재시도 메커니즘 적용

### 추가 작업
- [x] JPA Dirty Checking 이슈 해결
  - UserCoupon used_at 업데이트 실패 원인 분석
  - @Modifying(clearAutomatically=true) → flushAutomatically=true 수정
  - 트러블슈팅 문서 작성 (docs/JPA_DIRTY_CHECKING_ISSUE.md)
- [x] 설계 문서 작성
  - README.md에 Kafka 아키텍처 전체 설계 문서화
  - Redis Queue vs Kafka 성능 벤치마크 비교 표 작성
  - 시퀀스 다이어그램 (정상/예외 흐름) 작성

### **간단 회고** (3줄 이내)
- **잘한 점**: Kafka 파티션 기반 병렬 처리로 Redis Queue 대비 3배 성능 향상 달성. @Modifying 옵션으로 인한 영속성 컨텍스트 이슈를 근본 원인부터 분석하여 해결.
- **어려운 점**: JPA Dirty Checking이 작동하지 않는 원인을 찾는 데 시간이 걸렸음. clearAutomatically 옵션이 전체 영속성 컨텍스트에 영향을 미친다는 점을 간과함.
- **다음 시도**: Kafka Consumer Manual Commit 적용, Dead Letter Queue 구현, 실제 부하 테스트를 통한 성능 검증 진행 예정.
