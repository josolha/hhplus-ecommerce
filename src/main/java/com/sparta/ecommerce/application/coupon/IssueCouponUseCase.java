package com.sparta.ecommerce.application.coupon;

import com.sparta.ecommerce.application.coupon.dto.UserCouponResponse;
import com.sparta.ecommerce.domain.coupon.Coupon;
import com.sparta.ecommerce.domain.coupon.CouponRepository;
import com.sparta.ecommerce.domain.coupon.UserCoupon;
import com.sparta.ecommerce.domain.coupon.UserCouponRepository;
import com.sparta.ecommerce.domain.coupon.exception.CouponExpiredException;
import com.sparta.ecommerce.domain.coupon.exception.CouponIssueLockException;
import com.sparta.ecommerce.domain.coupon.exception.CouponSoldOutException;
import com.sparta.ecommerce.domain.coupon.exception.DuplicateCouponIssueException;
import com.sparta.ecommerce.domain.coupon.exception.InvalidCouponException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 쿠폰 발급 유스케이스
 *
 * 동시성 제어 전략:
 *
 * [인메모리 환경] (현재)
 * - ReentrantLock: 쿠폰별 개별 락 관리
 * - ConcurrentHashMap: 쿠폰 ID별 Lock 인스턴스 저장
 * - tryLock(timeout): 락 획득 대기 시간 제한 (10초)
 * - 성능 최적화: 서로 다른 쿠폰 발급은 동시 처리 가능
 * - 예: C001 발급과 C002 발급은 락 경쟁 없이 병렬 실행
 *
 * [데이터베이스 환경] (향후 마이그레이션 시)
 * - ReentrantLock 제거
 * - @Transactional + SELECT FOR UPDATE로 대체
 * - 또는 Redis 분산 락 (Redisson) 사용
 *
 * Lock Mechanism 장점:
 * 1. 공정성(Fairness): ReentrantLock(true)로 FIFO 순서 보장 가능
 * 2. 타임아웃: tryLock()으로 무한 대기 방지
 * 3. 세밀한 제어: lock/unlock 시점을 명시적으로 제어
 * 4. 성능: 쿠폰별 개별 락으로 병렬성 향상
 */
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 쿠폰별 Lock 관리
     * Key: 쿠폰 ID, Value: ReentrantLock 인스턴스
     *
     * ReentrantLock(true): Fair Lock - 대기 큐에서 먼저 온 스레드가 먼저 락 획득
     */
    private final ConcurrentHashMap<String, Lock> couponLocks = new ConcurrentHashMap<>();

    /**
     * 락 획득 대기 시간 (초)
     */
    private static final long LOCK_TIMEOUT_SECONDS = 10L;

    /**
     * 쿠폰 발급
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 사용자 쿠폰 정보
     *
     * 동시성 제어 흐름:
     * 1. 쿠폰 ID로 Lock 획득 시도 (최대 10초 대기)
     * 2. Lock 획득 성공 시:
     *    - 쿠폰 조회 및 유효성 검증
     *    - 중복 발급 체크
     *    - 재고 확인 및 차감
     *    - 사용자 쿠폰 발급
     * 3. Lock 해제 (finally 블록)
     * 4. Lock 획득 실패 시: CouponIssueLockException 발생
     *
     * 성능 특성:
     * - 같은 쿠폰(C001)을 동시에 발급받으려는 요청들만 순차 처리
     * - 다른 쿠폰(C001, C002)은 병렬 처리 가능
     *
     * @throws CouponIssueLockException Lock 획득 타임아웃 시
     * @throws InvalidCouponException 쿠폰이 존재하지 않을 때
     * @throws DuplicateCouponIssueException 이미 발급받은 쿠폰일 때
     * @throws CouponExpiredException 만료된 쿠폰일 때
     * @throws CouponSoldOutException 재고가 없을 때
     */
    public UserCouponResponse execute(String userId, String couponId) {
        // 1. 쿠폰별 Lock 인스턴스 가져오기 (없으면 생성)
        // computeIfAbsent: 원자적으로 Lock 생성 (ConcurrentHashMap의 동시성 보장)
        Lock lock = couponLocks.computeIfAbsent(couponId, k -> new ReentrantLock(true));

        // 2. Lock 획득 시도 (최대 10초 대기)
        boolean lockAcquired = false;
        try {
            lockAcquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!lockAcquired) {
                // Lock 획득 실패 = 다른 스레드가 장시간 점유 중
                throw new CouponIssueLockException(couponId, LOCK_TIMEOUT_SECONDS);
            }

            // 3. Lock 획득 성공 - 쿠폰 발급 로직 실행
            return issueCouponWithLock(userId, couponId);

        } catch (InterruptedException e) {
            // 스레드 인터럽트 발생 시
            Thread.currentThread().interrupt();
            throw new CouponIssueLockException(
                String.format("쿠폰[%s] 발급 처리 중 인터럽트가 발생했습니다", couponId));
        } finally {
            // 4. Lock 해제 (획득한 경우에만)
            if (lockAcquired) {
                lock.unlock();
            }
        }
    }

    /**
     * Lock 보호 하에 쿠폰 발급 실행
     *
     * 이 메서드는 Lock이 획득된 상태에서만 호출됨
     * 동일 쿠폰에 대한 동시 발급 시도가 직렬화됨
     */
    private UserCouponResponse issueCouponWithLock(String userId, String couponId) {
        // 1. 쿠폰 조회 (비관적 락)
        // 실제 DB: SELECT * FROM coupon WHERE id = ? FOR UPDATE
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new InvalidCouponException("존재하지 않는 쿠폰입니다"));

        // 2. 중복 발급 체크 (비관적 락)
        // 실제 DB: SELECT * FROM user_coupon WHERE user_id = ? AND coupon_id = ? FOR UPDATE
        if (userCouponRepository.existsByUserIdAndCouponIdWithLock(userId, couponId)) {
            throw new DuplicateCouponIssueException(couponId);
        }

        // 3. 만료 확인
        if (coupon.isExpired()) {
            throw new CouponExpiredException(couponId);
        }

        // 4. 재고 확인
        if (!coupon.hasStock()) {
            throw new CouponSoldOutException(couponId);
        }

        // 5. 쿠폰 재고 차감
        Coupon issuedCoupon = coupon.issue();
        couponRepository.save(issuedCoupon);

        // 6. 사용자 쿠폰 발급 이력 저장
        String userCouponId = "UC" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UserCoupon userCoupon = UserCoupon.issue(userCouponId, userId, issuedCoupon);
        userCouponRepository.save(userCoupon);

        // 7. 응답 생성
        return UserCouponResponse.from(userCoupon, issuedCoupon);
    }
}
