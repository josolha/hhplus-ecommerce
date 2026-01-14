package com.sparta.ecommerce.application.product;

import com.sparta.ecommerce.application.product.dto.PopularProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.List;

/**
 * Redis Cache-Aside vs Redis Sorted Set 성능 비교
 *
 * 목적: 두 방식의 실제 성능 차이를 측정하고 README에 반영
 */
@Slf4j
@SpringBootTest
public class RedisCacheVsSortedSetTest {

    @Autowired
    private GetPopularProductsUseCase getPopularProductsUseCase;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("Redis Cache-Aside vs Redis Sorted Set 성능 비교 (정밀)")
    void compareCacheVsSortedSet() {
        int warmupRounds = 5;
        int testRounds = 30;
        int days = 3;
        int limit = 5;

        log.info("=".repeat(80));
        log.info("Redis Cache-Aside vs Redis Sorted Set 성능 비교");
        log.info("데이터: 최근 {}일, Top {}", days, limit);
        log.info("=".repeat(80));

        // Warmup
        log.info("\n[Warmup 시작 - JVM 최적화]");
        for (int i = 0; i < warmupRounds; i++) {
            cacheManager.getCache("cache:popularProducts").clear();
            getPopularProductsUseCase.execute(days, limit);
            getPopularProductsUseCase.executeWithDatabase(days, limit);
        }

        // ========================================
        // 1. Redis Sorted Set 방식 (실시간 랭킹)
        // ========================================
        log.info("\n" + "=".repeat(80));
        log.info("【방식 1】 Redis Sorted Set (실시간 랭킹)");
        log.info("- ZUNIONSTORE (여러 날짜 병합) + ZREVRANGE (Top N) + DB 조회");
        log.info("- 주문 시마다 ZINCRBY로 실시간 업데이트");
        log.info("=".repeat(80));

        long sortedSetTotal = 0;
        for (int i = 0; i < testRounds; i++) {
            long start = System.nanoTime();
            List<PopularProductResponse> result = getPopularProductsUseCase.execute(days, limit);
            long elapsed = System.nanoTime() - start;
            double elapsedMs = elapsed / 1_000_000.0; // 나노초 → 밀리초
            sortedSetTotal += elapsed;
            log.info("Round {}: {:.2f}ms (결과: {} 건)", i + 1, elapsedMs, result.size());
        }
        double sortedSetAvg = (double) sortedSetTotal / testRounds / 1_000_000; // 나노초 → 밀리초

        // ========================================
        // 2. Redis Cache-Aside 방식
        // ========================================
        log.info("\n" + "=".repeat(80));
        log.info("【방식 2】 Redis Cache-Aside (TTL 기반 캐싱)");
        log.info("- Cache Miss: DB 집계 쿼리 → Redis 저장");
        log.info("- Cache Hit: Redis GET");
        log.info("=".repeat(80));

        // Cache Miss (첫 요청)
        cacheManager.getCache("cache:popularProducts").clear();
        long cacheMissStart = System.nanoTime();
        List<PopularProductResponse> cacheMissResult = getPopularProductsUseCase.executeWithDatabase(days, limit);
        long cacheMissTime = System.nanoTime() - cacheMissStart;
        double cacheMissMs = cacheMissTime / 1_000_000.0;
        log.info("Cache Miss (첫 요청): {:.2f}ms (결과: {} 건)", cacheMissMs, cacheMissResult.size());

        // Cache Hit (이후 요청들)
        long cacheHitTotal = 0;
        for (int i = 0; i < testRounds - 1; i++) {
            long start = System.nanoTime();
            List<PopularProductResponse> result = getPopularProductsUseCase.executeWithDatabase(days, limit);
            long elapsed = System.nanoTime() - start;
            double elapsedMs = elapsed / 1_000_000.0;
            cacheHitTotal += elapsed;
            log.info("Cache Hit Round {}: {:.2f}ms (결과: {} 건)", i + 1, elapsedMs, result.size());
        }
        double cacheHitAvg = (double) cacheHitTotal / (testRounds - 1) / 1_000_000;

        // ========================================
        // 3. DB 집계 쿼리 (인덱스 있음 - 비교용)
        // ========================================
        log.info("\n" + "=".repeat(80));
        log.info("【참고】 DB 집계 쿼리 (인덱스 있음)");
        log.info("- JOIN + GROUP BY + ORDER BY");
        log.info("=".repeat(80));

        // 캐시 제거하고 실제 DB 조회만 측정
        cacheManager.getCache("cache:popularProducts").clear();
        long dbTotal = 0;
        for (int i = 0; i < testRounds; i++) {
            cacheManager.getCache("cache:popularProducts").clear();
            long start = System.nanoTime();
            List<PopularProductResponse> result = getPopularProductsUseCase.executeWithDatabase(days, limit);
            long elapsed = System.nanoTime() - start;
            double elapsedMs = elapsed / 1_000_000.0;
            dbTotal += elapsed;
            log.info("Round {}: {:.2f}ms (결과: {} 건)", i + 1, elapsedMs, result.size());
        }
        double dbAvg = (double) dbTotal / testRounds / 1_000_000;

        // ========================================
        // 최종 결과 비교
        // ========================================
        log.info("\n" + "=".repeat(80));
        log.info("성능 비교 결과 요약");
        log.info("=".repeat(80));
        log.info("방식                       | 평균 응답 시간 | 실시간성 | DB 부하 |");
        log.info("-".repeat(80));
        log.info(String.format("Redis Sorted Set (실시간)  | %.2f ms       | ✅ 즉시  | 🟢 낮음  |", sortedSetAvg));
        log.info(String.format("Redis Cache (Cache Hit)    | %.2f ms       | ❌ 지연  | 🟢 낮음  |", cacheHitAvg));
        log.info(String.format("Redis Cache (Cache Miss)   | %.2f ms       | ❌ 지연  | 🟡 높음  |", cacheMissMs));
        log.info(String.format("DB 집계 (인덱스 있음)       | %.2f ms       | ✅ 즉시  | 🟡 중간  |", dbAvg));
        log.info("=".repeat(80));

        log.info("\n【선택 기준】");
        log.info("- 실시간성 필수 + 초고트래픽    → Redis Sorted Set");
        log.info("- 실시간성 불필요 + 빠른 응답   → Redis Cache");
        log.info("- 트래픽 적음 + 실시간성 필요   → DB 집계 (인덱스)");
        log.info("=".repeat(80));
    }
}
