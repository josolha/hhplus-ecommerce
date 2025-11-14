## :pushpin: PR 제목 규칙
[STEP08] 인기 상품 조회 쿼리 최적화 및 인덱스 설계

---
## **과제 체크리스트** :white_check_mark:

### ✅ **STEP07: DB 설계 개선 및 구현** (필수)
- [x] 기존 설계된 테이블 구조에 대한 개선점이 반영되었는가? (선택)
- [x] Repository 및 데이터 접근 계층이 역할에 맞게 분리되어 있는가?
- [x] MySQL 기반으로 연동되고 동작하는가?
- [x] infrastructure 레이어를 포함하는 통합 테스트가 작성되었는가?
- [x] 핵심 기능에 대한 흐름이 테스트에서 검증되었는가?
- [x] 기존에 작성된 동시성 테스트가 잘 통과하는가?

### 🔥 **STEP08: 쿼리 및 인덱스 최적화** (심화)
- [x] 조회 성능 저하 가능성이 있는 기능을 식별하였는가?
- [x] 쿼리 실행계획(Explain) 기반으로 문제를 분석하였는가?
- [x] 인덱스 설계 또는 쿼리 구조 개선 등 해결방안을 도출하였는가?

---
## 🔗 **주요 구현 커밋**

- JPA Entity 인덱스 어노테이션 추가: `[커밋 링크]`
- README.md 인덱스 최적화 문서 작성 (4단계 실험 포함): `[커밋 링크]`

---
## 📊 **최적화 결과 요약**

### 인기 상품 조회 쿼리 (4단계 실험)

| 단계 | 인덱스 구조 | 실행 시간 | 개선율 | 결과 |
|------|------------|----------|--------|------|
| 1단계 | 인덱스 없음 | 2,930ms | 기준 | ❌ Full Scan |
| 2단계 | orders만 | 4,584ms | -56% | ❌ 악화 |
| 3단계 | orders + order_items 분리 | **840ms** | **+71.3%** | ✅ **선택** |
| 4단계 | Covering Index | 1,017ms | +65.3% | ❌ 느림 |

**최종 선택: 3단계 (71.3% 성능 개선)**

### 적용한 인덱스 (총 6개)

```sql
-- 1. 인기 상품 조회 최적화
CREATE INDEX idx_orders_status_created_at ON orders(status, created_at);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- 2. 사용자별 주문 조회
CREATE INDEX idx_orders_user_id_status ON orders(user_id, status);

-- 3. 카테고리별 상품 정렬
CREATE INDEX idx_products_category_price ON products(category, price);

-- 4. 쿠폰 중복 발급 방지
CREATE INDEX idx_user_coupons_user_coupon ON user_coupons(user_id, coupon_id);
```

### 주요 개선 사항
- ✅ **실행 시간**: 2,930ms → 840ms (71.3% 개선)
- ✅ **처리 데이터**: 400,000건 → 11,992건 (97% 감소)
- ✅ **JOIN 순서 최적화**: order_items → orders → products에서 orders → order_items → products로 변경
- ✅ **Left-most prefix rule 적용**: 중복 인덱스 제거 (8개 → 6개)

---
## 💬 **리뷰 요청 사항**

### 질문/고민 포인트
1. **Covering Index가 오히려 느린 이유**: 이론적으로 Covering Index가 더 빠를 것으로 예상했지만, 실측 결과 21% 느렸습니다. 인덱스 크기 증가와 조인 횟수가 적은 것(11,992번)이 원인으로 분석했는데, 이 판단이 합당한지 검토 부탁드립니다.

2. **Left-most prefix rule 적용**: `idx_orders_user_id_status` 인덱스가 `user_id` 단독 조회도 커버할 수 있어 `idx_orders_user_id`를 제거했습니다. 실무에서도 이런 방식으로 중복 인덱스를 제거하는 것이 일반적인지 궁금합니다.

3. **인덱스 개수 vs 성능**: 현재 6개 인덱스를 적용했는데, INSERT/UPDATE 성능과의 트레이드오프를 고려할 때 적절한 수준인지 의견 부탁드립니다.

### 특별히 리뷰받고 싶은 부분
- `README.md`의 4단계 최적화 문서가 이해하기 쉽게 작성되었는지
- JPA Entity의 `@Index` 어노테이션 사용이 적절한지 (특히 복합 인덱스 컬럼 순서)
- Left-most prefix rule을 적용한 중복 인덱스 제거가 실무적으로 타당한지

---
## 📊 **성능 검증**

| 항목 | 결과 |
|------|------|
| 인기 상품 조회 쿼리 (인덱스 전) | 2,930ms |
| 인기 상품 조회 쿼리 (인덱스 후) | 840ms |
| 성능 개선율 | **71.3%** |
| 처리 데이터 감소 | 400,000건 → 11,992건 (97% 감소) |
| 실험 단계 | 4단계 (인덱스 없음 → orders → order_items → Covering Index) |

### 검증 방법
- EXPLAIN으로 실행 계획 분석
- EXPLAIN ANALYZE로 실제 성능 측정
- 4가지 인덱스 전략 비교 실험

---
## 📝 **회고**

### ✨ 잘한 점
- **4단계 실험을 통한 체계적 검증**: 단순히 인덱스를 추가하는 것이 아니라, 4단계에 걸쳐 실험하고 비교 분석하여 최적의 전략을 선택했습니다. 특히 Covering Index가 이론적으로 좋아보여도 실제로는 느릴 수 있다는 것을 실측으로 확인한 것이 의미있었습니다.

- **Left-most prefix rule 적용**: MySQL의 복합 인덱스 특성을 이해하고 중복 인덱스를 제거하여 8개에서 6개로 줄였습니다. 이를 통해 INSERT/UPDATE 성능 저하를 최소화하면서도 조회 성능을 유지할 수 있었습니다.

- **상세한 문서화**: console_2.sql에 모든 실험 과정을 EXPLAIN/EXPLAIN ANALYZE 결과와 함께 기록하고, README.md에 이해하기 쉽게 정리했습니다. 나중에 다른 개발자가 보거나 제가 다시 볼 때 왜 이런 결정을 내렸는지 명확히 알 수 있습니다.

### 😓 어려웠던 점
- **MySQL 옵티마이저의 예측 불가능성**: 2단계에서 orders 인덱스만 추가했을 때 오히려 56% 느려진 것을 보고 당황했습니다. 인덱스가 있어도 옵티마이저가 사용하지 않을 수 있다는 것을 배웠고, JOIN 순서를 바꾸려면 모든 테이블에 인덱스가 필요하다는 것을 실감했습니다.

- **이론과 실제의 차이**: Covering Index가 이론적으로 완벽해 보였지만 실제로는 더 느렸습니다. 인덱스 크기, 조인 횟수, 캐싱 등 여러 요인을 종합적으로 고려해야 한다는 것을 배웠습니다. 앞으로는 "실측이 정답"이라는 마음가짐으로 작업해야겠습니다.

- **JPA 인덱스 어노테이션 순서**: `@Table(indexes = ...)` 어노테이션에서 복합 인덱스의 컬럼 순서가 SQL과 동일하게 적용되는지 확신이 서지 않아 여러 번 확인했습니다. 공식 문서를 찾아보며 `columnList = "status, created_at"` 형식이 맞다는 것을 확인했습니다.

### 🚀 다음에 시도할 것
- **대용량 데이터에서 재실험**: 현재는 40만 건 데이터로 테스트했는데, 수백만 건 이상의 데이터에서는 Covering Index가 효과적일 수 있습니다. 실무에서 데이터가 쌓이면 다시 테스트해보고 싶습니다.

- **쿼리 캐싱 적용**: 인기 상품은 5분마다 갱신해도 충분할 것 같습니다. Redis나 Spring Cache를 적용하면 840ms를 수 ms로 줄일 수 있을 것 같습니다.

- **인덱스 모니터링**: 실제로 인덱스가 잘 사용되고 있는지, 사용되지 않는 인덱스는 없는지 주기적으로 모니터링하는 시스템을 구축하고 싶습니다.

---
## 📚 **참고 자료**

- [MySQL 8.0 Reference Manual - EXPLAIN](https://dev.mysql.com/doc/refman/8.0/en/explain.html) - 실행 계획 분석 방법
- [MySQL 8.0 Reference Manual - Optimization and Indexes](https://dev.mysql.com/doc/refman/8.0/en/optimization-indexes.html) - 인덱스 최적화 가이드
- [Use The Index, Luke!](https://use-the-index-luke.com/) - 인덱스 설계 원칙 (특히 복합 인덱스 컬럼 순서)
- [High Performance MySQL](https://www.oreilly.com/library/view/high-performance-mysql/9781492080503/) - Covering Index와 InnoDB 클러스터드 인덱스 특성

---
## ✋ **체크리스트 (제출 전 확인)**

- [x] 적절한 ORM을 사용하였는가? (JPA, TypeORM, Prisma, Sequelize 등)
- [x] Repository 전환 간 서비스 로직의 변경은 없는가?
- [x] docker-compose, testcontainers 등 로컬 환경에서 실행하고 테스트할 수 있는 환경을 구성했는가?
- [x] EXPLAIN/EXPLAIN ANALYZE로 쿼리 성능을 실측했는가?
- [x] 인덱스 최적화 근거가 문서화되어 있는가?
- [x] JPA Entity에 인덱스가 적절히 정의되어 있는가?