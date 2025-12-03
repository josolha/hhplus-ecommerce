# Redis 직렬화 문제 해결 과정

## 문제 상황

### 발생한 에러
```
org.springframework.data.redis.serializer.SerializationException:
Could not read JSON: Invalid numeric value: Leading zeroes not allowed
at org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer.deserialize
```

### 발생 시점
- **기능**: 인기 상품 랭킹 조회 API 호출
- **위치**: `ProductRankingService.getTopProductsWithScore()`
- **상황**: Redis Sorted Set에서 데이터 조회 시

---

## 원인 분석

### 1. RedisConfig 설정 확인

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 직렬화: String
        template.setKeySerializer(new StringRedisSerializer());

        // Value 직렬화: JSON ← 문제의 원인!
        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(objectMapper());
        template.setValueSerializer(serializer);

        return template;
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // LocalDateTime 지원
        mapper.registerModule(new JavaTimeModule());

        // 날짜를 문자열로 직렬화
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 타입 정보 포함 (역직렬화 시 클래스 정보 필요)
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

        return mapper;
    }
}
```

### 2. 문제의 핵심: GenericJackson2JsonRedisSerializer

#### ObjectMapper의 activateDefaultTyping 설정

```java
mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
```

**이 설정의 의미:**
- Redis에 저장할 때 **타입 정보를 함께 저장**
- 역직렬화 시 원래 Java 타입으로 복원하기 위함
- **모든 값을 JSON 형태로 감싸서 저장**

#### 실제 저장되는 데이터 형태

**저장 시도한 값:**
```java
redisTemplate.opsForZSet().incrementScore("product:ranking", "UUID-123", 1.0);
```

**Redis에 실제로 저장된 형태:**
```json
[
  "java.lang.String",
  "UUID-123"
]
```

또는

```json
{
  "@class": "java.lang.String",
  "value": "UUID-123"
}
```

### 3. 에러 발생 과정

#### Step 1: Sorted Set 조회
```java
Set<TypedTuple<Object>> topProductsWithScores =
    redisTemplate.opsForZSet().reverseRangeWithScores(mergedKey, 0, limit - 1);
```

#### Step 2: Redis에서 가져온 데이터
```
member: ["java.lang.String", "UUID-123"]
score: 25.0
```

#### Step 3: 역직렬화 시도
```java
GenericJackson2JsonRedisSerializer.deserialize(data)
```

Jackson이 다음과 같은 JSON을 파싱 시도:
```json
["java.lang.String", "UUID-123"]
```

#### Step 4: 파싱 실패
```
에러: Invalid numeric value: Leading zeroes not allowed
```

**왜 이 에러?**
- Jackson이 배열 형태의 복잡한 JSON 구조를 파싱
- 내부적으로 숫자 파싱 중 형식 오류 발생
- Sorted Set의 member는 단순 String이어야 하는데 복잡한 JSON 객체가 들어감

---

## 해결 방법

### StringRedisTemplate 사용

#### 1. RedisConfig에 StringRedisTemplate 추가

```java
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate 설정 (캐싱용 - 복잡한 객체 저장)
     * 현재 프로젝트에서 미사용
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // ... 기존 설정 유지
    }

    /**
     * StringRedisTemplate 설정 (Sorted Set 랭킹용)
     * 단순 String 직렬화만 사용
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
```

#### 2. ProductRankingService 수정

**변경 전:**
```java
@Service
@RequiredArgsConstructor
public class ProductRankingService {

    private final RedisTemplate<String, Object> redisTemplate;  // ❌

    public void incrementPurchaseCount(String productId) {
        redisTemplate.opsForZSet()
            .incrementScore(todayKey, productId, 1.0);
    }
}
```

**변경 후:**
```java
@Service
@RequiredArgsConstructor
public class ProductRankingService {

    private final StringRedisTemplate redisTemplate;  // ✅

    public void incrementPurchaseCount(String productId) {
        redisTemplate.opsForZSet()
            .incrementScore(todayKey, productId, 1.0);
    }
}
```

---

## StringRedisTemplate vs RedisTemplate 차이점

### 비교표

| 항목 | RedisTemplate<String, Object> | StringRedisTemplate |
|------|-------------------------------|---------------------|
| **타입** | `RedisTemplate<String, Object>` | `RedisTemplate<String, String>` |
| **Key 직렬화** | StringRedisSerializer | StringRedisSerializer |
| **Value 직렬화** | GenericJackson2JsonRedisSerializer | StringRedisSerializer |
| **저장 형태** | JSON (타입 정보 포함) | 순수 문자열 |
| **용도** | 복잡한 객체 (DTO, List 등) | 단순 문자열, Sorted Set |
| **Sorted Set** | ❌ 파싱 에러 발생 | ✅ 정상 동작 |

### 실제 저장 데이터 비교

#### 예시: "UUID-123" 저장

**RedisTemplate<String, Object> 사용 시:**
```bash
127.0.0.1:6379> ZRANGE product:ranking 0 -1 WITHSCORES
1) "[\"java.lang.String\",\"UUID-123\"]"
2) "25"
```

**StringRedisTemplate 사용 시:**
```bash
127.0.0.1:6379> ZRANGE product:ranking 0 -1 WITHSCORES
1) "UUID-123"
2) "25"
```

---

## 왜 이런 설계를 했는가?

### RedisTemplate<String, Object>의 목적

**복잡한 객체를 Redis에 저장하기 위함**

```java
// DTO를 캐시에 저장
ProductResponse product = new ProductResponse("노트북", 1000000, 10);
redisTemplate.opsForValue().set("product:1", product);

// 저장 형태
{
  "@class": "com.sparta.ecommerce.application.product.dto.ProductResponse",
  "name": "노트북",
  "price": 1000000,
  "stock": 10
}
```

**장점:**
- Java 객체를 그대로 저장/조회 가능
- 타입 안정성 보장
- 역직렬화 시 원래 클래스로 복원

**단점:**
- Sorted Set 같은 단순 자료구조에는 오버스펙
- JSON 파싱 오버헤드
- 타입 정보로 인한 용량 증가

### StringRedisTemplate의 목적

**단순 문자열 데이터만 다룰 때**

```java
// Sorted Set: 랭킹
stringRedisTemplate.opsForZSet().add("product:ranking", "UUID-123", 25.0);

// String: 카운터
stringRedisTemplate.opsForValue().increment("visit:count");

// Set: 중복 제거
stringRedisTemplate.opsForSet().add("tags", "Java", "Spring", "Redis");
```

**장점:**
- 직렬화/역직렬화 오버헤드 없음
- Redis CLI에서 직접 읽기 가능
- 용량 효율적

**단점:**
- 복잡한 객체 저장 불가능
- 수동 직렬화/역직렬화 필요

---

## 실제 사용 시나리오

### Scenario 1: 캐싱 (RedisTemplate 사용)

```java
@Service
public class ProductCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void cacheProduct(ProductResponse product) {
        // 복잡한 객체를 JSON으로 저장
        redisTemplate.opsForValue()
            .set("cache:product:" + product.productId(), product, 5, TimeUnit.MINUTES);
    }

    public ProductResponse getProduct(String productId) {
        // JSON을 Java 객체로 역직렬화
        return (ProductResponse) redisTemplate.opsForValue()
            .get("cache:product:" + productId);
    }
}
```

### Scenario 2: 랭킹 (StringRedisTemplate 사용)

```java
@Service
public class ProductRankingService {

    private final StringRedisTemplate redisTemplate;

    public void incrementPurchaseCount(String productId) {
        // 단순 String만 저장
        String todayKey = "product:ranking:" + LocalDate.now();
        redisTemplate.opsForZSet()
            .incrementScore(todayKey, productId, 1.0);
    }

    public List<String> getTopProducts(int limit) {
        // 단순 String만 조회
        Set<String> topIds = redisTemplate.opsForZSet()
            .reverseRange("product:ranking:" + LocalDate.now(), 0, limit - 1);
        return new ArrayList<>(topIds);
    }
}
```

---

## 최종 해결 결과

### 변경 사항 요약

1. **RedisConfig**: `StringRedisTemplate` Bean 추가
2. **ProductRankingService**: 의존성을 `StringRedisTemplate`로 변경
3. **타입 변경**: `TypedTuple<Object>` → `TypedTuple<String>`

### 동작 확인

#### Redis 데이터 저장
```bash
# 주문 완료 시 랭킹 증가
ZADD product:ranking:2025-12-03 1 "550e8400-e29b-41d4-a716-446655440000"
ZINCRBY product:ranking:2025-12-03 1 "550e8400-e29b-41d4-a716-446655440000"

# 확인
127.0.0.1:6379> ZREVRANGE product:ranking:2025-12-03 0 4 WITHSCORES
1) "550e8400-e29b-41d4-a716-446655440000"
2) "2"
```

#### API 응답
```json
[
  {
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "노트북",
    "price": 1500000,
    "stock": 10,
    "category": "전자제품",
    "salesCount": 2
  }
]
```

---

## 교훈

### 1. 직렬화 방식 선택의 중요성
- **데이터 구조에 맞는 직렬화 방식 선택 필수**
- 복잡한 객체 ≠ 단순 문자열

### 2. Redis 자료구조별 최적 설정

| 자료구조 | 권장 직렬화 방식 |
|---------|----------------|
| String (캐싱) | JSON |
| Sorted Set (랭킹) | String |
| Set (태그, 중복 제거) | String |
| Hash (필드-값 쌍) | JSON |
| List (큐) | String 또는 JSON |

### 3. 설정의 영향 범위 이해
- `ObjectMapper.activateDefaultTyping()`의 영향
- Value Serializer가 모든 값에 적용됨
- 용도별 RedisTemplate 분리 필요

---

## 참고 자료

### Spring Data Redis 공식 문서
- [RedisTemplate](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis:template)
- [Redis Serializers](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis:serializer)

### Jackson 직렬화
- [Polymorphic Type Handling](https://github.com/FasterXML/jackson-docs/wiki/JacksonPolymorphicDeserialization)
- [DefaultTyping](https://fasterxml.github.io/jackson-databind/javadoc/2.9/com/fasterxml/jackson/databind/ObjectMapper.DefaultTyping.html)

---

## 작성 정보
- **작성일**: 2025-12-03
- **작성자**: 개발팀
- **관련 이슈**: Redis Serialization Exception
- **해결 방법**: StringRedisTemplate 도입
