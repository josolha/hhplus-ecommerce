package com.sparta.ecommerce.application.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 쿠폰 발급 Redis 관리 서비스
 *
 * 역할:
 * - Redis Set을 활용한 중복 발급 방지
 * - 빠른 중복 체크로 API 응답 속도 유지
 *
 * 사용 방식:
 * - Kafka 방식: Redis Set으로 중복 체크 + Kafka로 메시지 큐잉
 * - Set 키: coupon:issued:{couponId}
 * - 값: userId (발급 요청한 사용자 목록)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueRedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String ISSUED_SET_PREFIX = "coupon:issued:";
    private static final String SOLD_OUT_FLAG_PREFIX = "coupon:sold-out:";

    /**
     * Redis Set에 사용자 추가 (중복 체크)
     * Kafka 방식에서 사용
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return 추가 성공 시 1, 이미 존재 시 0
     */
    public Long addToIssuedSet(String couponId, String userId) {
        String issuedSetKey = ISSUED_SET_PREFIX + couponId;

        // Set에 추가 시도 (SADD - atomic operation)
        Long added = redisTemplate.opsForSet().add(issuedSetKey, userId);

        if (added != null && added > 0) {
            log.debug("중복 체크 통과 - Set에 추가: userId={}, couponId={}", userId, couponId);
        } else {
            log.debug("중복 발급 요청 감지: userId={}, couponId={}", userId, couponId);
        }

        return added;
    }

    /**
     * 발급 요청 여부 확인
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return true: 발급 요청함, false: 요청 안 함
     */
    public boolean hasRequested(String couponId, String userId) {
        String issuedSetKey = ISSUED_SET_PREFIX + couponId;
        Boolean isMember = redisTemplate.opsForSet().isMember(issuedSetKey, userId);
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 발급 실패 시 Set에서 제거 (재시도 가능하도록)
     * Consumer에서 처리 실패 시 호출
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     */
    public void removeFromIssuedSet(String couponId, String userId) {
        String issuedSetKey = ISSUED_SET_PREFIX + couponId;
        redisTemplate.opsForSet().remove(issuedSetKey, userId);
        log.debug("발급 실패로 Set에서 제거 (재시도 가능): userId={}, couponId={}", userId, couponId);
    }

    /**
     * 재고 소진 플래그 설정
     * 재고가 소진되면 플래그를 설정하여 후속 요청을 빠르게 차단
     *
     * @param couponId 쿠폰 ID
     */
    public void setSoldOutFlag(String couponId) {
        String soldOutKey = SOLD_OUT_FLAG_PREFIX + couponId;
        redisTemplate.opsForValue().set(soldOutKey, "true");
        redisTemplate.expire(soldOutKey, 1, java.util.concurrent.TimeUnit.HOURS);
        log.info("재고 소진 플래그 설정: couponId={}", couponId);
    }

    /**
     * 재고 소진 여부 확인
     *
     * @param couponId 쿠폰 ID
     * @return true: 재고 소진, false: 재고 있음
     */
    public boolean isSoldOut(String couponId) {
        String soldOutKey = SOLD_OUT_FLAG_PREFIX + couponId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(soldOutKey));
    }

    /**
     * Redis 재고 초기화 (쿠폰 생성 시)
     *
     * @param couponId 쿠폰 ID
     * @param quantity 초기 재고 수량
     */
    public void initializeStock(String couponId, Integer quantity) {
        String stockKey = "coupon:stock:" + couponId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(quantity));
        log.info("Redis 재고 초기화: couponId={}, quantity={}", couponId, quantity);
    }

    /**
     * Redis 재고 1 감소 (원자적)
     *
     * @param couponId 쿠폰 ID
     * @return 감소 후 남은 재고 (음수 가능)
     */
    public Long decrementStock(String couponId) {
        String stockKey = "coupon:stock:" + couponId;
        Long remaining = redisTemplate.opsForValue().decrement(stockKey);
        log.debug("Redis 재고 감소: couponId={}, remaining={}", couponId, remaining);
        return remaining;
    }

    /**
     * Redis 재고 1 증가 (롤백/복구용)
     *
     * @param couponId 쿠폰 ID
     * @return 증가 후 재고
     */
    public Long incrementStock(String couponId) {
        String stockKey = "coupon:stock:" + couponId;
        Long remaining = redisTemplate.opsForValue().increment(stockKey);
        log.debug("Redis 재고 복구: couponId={}, remaining={}", couponId, remaining);
        return remaining;
    }
}
