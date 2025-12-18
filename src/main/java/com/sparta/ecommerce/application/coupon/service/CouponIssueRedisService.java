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
}
