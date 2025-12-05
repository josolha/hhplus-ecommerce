package com.sparta.ecommerce.application.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 쿠폰 발급 큐 관리 서비스 (Redis Blocking Queue)
 *
 * 구조:
 * - Queue: coupon:queue:{couponId} (List - BRPOP으로 블로킹 처리)
 * - Issued Set: coupon:issued:{couponId} (Set) - 중복 방지용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponQueueService {

    private final StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "coupon:queue:";
    private static final String ISSUED_SET_PREFIX = "coupon:issued:";

    /**
     * 쿠폰 발급 요청을 큐에 추가
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     * @return true: 큐에 추가 성공, false: 이미 발급 요청함
     */
    public boolean addToQueue(String couponId, String userId) {
        String issuedSetKey = ISSUED_SET_PREFIX + couponId;

        // 1. 이미 발급 요청했는지 확인 (Set에 추가 시도)
        Long added = redisTemplate.opsForSet().add(issuedSetKey, userId);

        if (added == null || added == 0) {
            log.debug("이미 발급 요청한 사용자: userId={}, couponId={}", userId, couponId);
            return false;
        }

        // 2. 큐에 추가 (LPUSH: 왼쪽에 추가)
        String queueKey = QUEUE_KEY_PREFIX + couponId;
        redisTemplate.opsForList().leftPush(queueKey, userId);

        log.debug("큐에 추가 완료: userId={}, couponId={}", userId, couponId);
        return true;
    }

    /**
     * 큐에서 발급 요청 꺼내기 (Blocking)
     * 데이터가 없으면 최대 5초 대기
     *
     * @param couponId 쿠폰 ID
     * @return 사용자 ID (타임아웃 시 null)
     */
    public String blockingPopFromQueue(String couponId) {
        String queueKey = QUEUE_KEY_PREFIX + couponId;

        // BRPOP: 블로킹 POP (최대 5초 대기)
        String userId = redisTemplate.opsForList().rightPop(queueKey, 5, TimeUnit.SECONDS);

        if (userId != null) {
            log.debug("큐에서 꺼냄 (Blocking): userId={}, couponId={}", userId, couponId);
        }

        return userId;
    }

    /**
     * 큐에서 발급 요청 꺼내기 (Non-blocking)
     *
     * @param couponId 쿠폰 ID
     * @return 사용자 ID (큐가 비어있으면 null)
     */
    public String popFromQueue(String couponId) {
        String queueKey = QUEUE_KEY_PREFIX + couponId;

        // RPOP: 오른쪽에서 꺼내기 (FIFO 순서)
        String userId = redisTemplate.opsForList().rightPop(queueKey);

        if (userId != null) {
            log.debug("큐에서 꺼냄: userId={}, couponId={}", userId, couponId);
        }

        return userId;
    }

    /**
     * 큐 크기 조회
     *
     * @param couponId 쿠폰 ID
     * @return 대기 중인 요청 수
     */
    public long getQueueSize(String couponId) {
        String queueKey = QUEUE_KEY_PREFIX + couponId;
        Long size = redisTemplate.opsForList().size(queueKey);
        return size != null ? size : 0L;
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
     *
     * @param couponId 쿠폰 ID
     * @param userId 사용자 ID
     */
    public void removeFromIssuedSet(String couponId, String userId) {
        String issuedSetKey = ISSUED_SET_PREFIX + couponId;
        redisTemplate.opsForSet().remove(issuedSetKey, userId);
        log.debug("발급 실패로 Set에서 제거: userId={}, couponId={}", userId, couponId);
    }
}
