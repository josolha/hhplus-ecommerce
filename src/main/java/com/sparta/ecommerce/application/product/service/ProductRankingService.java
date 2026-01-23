package com.sparta.ecommerce.application.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 상품 랭킹 관리 서비스 (Redis 기반)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRankingService {

    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private static final String RANKING_KEY_PREFIX = "product:ranking";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 구매 완료 시 상품 랭킹 점수 증가
     */
    public void incrementPurchaseCount(String productId) {
        try {
            String todayKey = getTodayRankingKey();
            redisTemplate.opsForZSet()
                    .incrementScore(todayKey, productId, 1.0);
            log.debug("상품 랭킹 증가: productId={}, key={}", productId, todayKey);
        } catch (Exception e) {
            // 랭킹 업데이트 실패해도 주문은 성공해야 함
            log.error("상품 랭킹 업데이트 실패: productId={}", productId, e);
        }
    }

    /**
     * 최근 N일 기준 Top N 상품 ID와 점수 조회
     */
    public List<RankingItem> getTopProductsWithScore(int days, int limit) {
        try {
            String mergedKey = "product:ranking:merged:" + days + "days:temp";
            List<String> keys = getLastNDaysKeys(days);

            if (keys.isEmpty()) {
                log.warn("Redis에 랭킹 데이터 없음: days={}", days);
                return List.of();
            }

            // ZUNIONSTORE: 여러 Sorted Set을 하나로 병합 (점수 합산)
            String firstKey = keys.get(0);
            List<String> otherKeys = keys.subList(1, keys.size());

            redisTemplate.opsForZSet()
                    .unionAndStore(firstKey, otherKeys, mergedKey);

            // Top N 조회 (점수 포함)
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> topProductsWithScores =
                    redisTemplate.opsForZSet()
                            .reverseRangeWithScores(mergedKey, 0, limit - 1);

            // 임시 키 삭제
            redisTemplate.delete(mergedKey);

            // TypedTuple → RankingItem 변환
            List<RankingItem> rankingItems = topProductsWithScores.stream()
                    .map(tuple -> new RankingItem(
                            tuple.getValue(),  // 이미 String
                            tuple.getScore().longValue()
                    ))
                    .collect(Collectors.toList());

            log.debug("Redis 랭킹 조회 완료: days={}, limit={}, size={}", days, limit, rankingItems.size());
            return rankingItems;

        } catch (Exception e) {
            log.error("Redis 랭킹 조회 실패: days={}, limit={}", days, limit, e);
            return List.of();
        }
    }

    /**
     * 랭킹 아이템 (상품 ID + 점수)
     */
    public record RankingItem(String productId, Long score) {}

    /**
     * 오늘 날짜 기준 랭킹 키 생성
     */
    private String getTodayRankingKey() {
        LocalDate today = LocalDate.now();
        return RANKING_KEY_PREFIX + ":" + today.format(DATE_FORMATTER);
    }

    /**
     * 최근 N일 키 리스트 생성
     */
    private List<String> getLastNDaysKeys(int days) {
        LocalDate today = LocalDate.now();

        return Stream.iterate(today, date -> date.minusDays(1))  // 오늘부터 하루씩 과거로
                .limit(days)  // N일만큼만 생성
                .map(date -> RANKING_KEY_PREFIX + ":" + date.format(DATE_FORMATTER))  // "product:ranking:2025-12-03" 형식으로 변환
                .filter(key -> Boolean.TRUE.equals(redisTemplate.hasKey(key)))  // Redis에 실제 존재하는 키만 필터링
                .collect(Collectors.toList());
    }
}
