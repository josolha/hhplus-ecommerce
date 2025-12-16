package com.sparta.ecommerce.application.product;

import com.sparta.ecommerce.application.product.dto.PopularProductResponse;

import com.sparta.ecommerce.application.product.usecase.GetPopularProductsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Redis 랭킹 vs DB 집계 쿼리 성능 비교 테스트
 *
 * 목적: Step 12 (DB 캐시) vs Step 13 (Redis 실시간) 성능 차이 측정
 */
@Slf4j
@SpringBootTest
public class RankingPerformanceComparisonTest {

    @Autowired
    private GetPopularProductsUseCase getPopularProductsUseCase;

    @Test
    @DisplayName("Redis 랭킹 vs DB 집계 속도 비교 (10회 평균)")
    void comparePerformance() {
        int warmupRounds = 3;
        int testRounds = 10;
        int days = 3;
        int limit = 5;

        // Warmup (JVM 최적화)
        log.info("=== Warmup 시작 ===");
        for (int i = 0; i < warmupRounds; i++) {
            getPopularProductsUseCase.execute(days, limit);
            getPopularProductsUseCase.executeWithDatabase(days, limit);
        }

        // Redis 방식 테스트
        log.info("\n=== Redis 랭킹 방식 테스트 시작 ===");
        long redisTotal = 0;
        for (int i = 0; i < testRounds; i++) {
            long start = System.currentTimeMillis();
            List<PopularProductResponse> result = getPopularProductsUseCase.execute(days, limit);
            long elapsed = System.currentTimeMillis() - start;
            redisTotal += elapsed;
            log.info("Round {}: {}ms (결과: {} 건)", i + 1, elapsed, result.size());
        }
        double redisAvg = (double) redisTotal / testRounds;

        // DB 집계 방식 테스트
        log.info("\n=== DB 집계 쿼리 방식 테스트 시작 ===");
        long dbTotal = 0;
        for (int i = 0; i < testRounds; i++) {
            long start = System.currentTimeMillis();
            List<PopularProductResponse> result = getPopularProductsUseCase.executeWithDatabase(days, limit);
            long elapsed = System.currentTimeMillis() - start;
            dbTotal += elapsed;
            log.info("Round {}: {}ms (결과: {} 건)", i + 1, elapsed, result.size());
        }
        double dbAvg = (double) dbTotal / testRounds;

        // 결과 출력
        log.info("\n" + "=".repeat(60));
        log.info("성능 비교 결과 ({}일 기준, Top {})", days, limit);
        log.info("=".repeat(60));
        log.info("Redis 랭킹 평균: {:.2f}ms", redisAvg);
        log.info("DB 집계 쿼리 평균: {:.2f}ms", dbAvg);
        log.info("성능 향상: {:.1f}배 빠름", dbAvg / redisAvg);
        log.info("=".repeat(60));
    }

    @Test
    @DisplayName("Redis 방식 단일 조회 테스트")
    void testRedisRanking() {
        long start = System.currentTimeMillis();
        List<PopularProductResponse> result = getPopularProductsUseCase.execute(3, 5);
        long elapsed = System.currentTimeMillis() - start;

        log.info("Redis 조회 완료: {}ms", elapsed);
        log.info("결과: {} 건", result.size());
        result.forEach(product ->
                log.info("- {} (ID: {})", product.name(), product.productId())
        );
    }

    @Test
    @DisplayName("DB 집계 방식 단일 조회 테스트")
    void testDatabaseAggregation() {
        long start = System.currentTimeMillis();
        List<PopularProductResponse> result = getPopularProductsUseCase.executeWithDatabase(3, 5);
        long elapsed = System.currentTimeMillis() - start;

        log.info("DB 집계 조회 완료: {}ms", elapsed);
        log.info("결과: {} 건", result.size());
        result.forEach(product ->
                log.info("- {} (ID: {}, 판매량: {})", product.name(), product.productId(), product.salesCount())
        );
    }
}
