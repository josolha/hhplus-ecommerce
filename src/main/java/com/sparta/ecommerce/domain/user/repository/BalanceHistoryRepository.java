package com.sparta.ecommerce.domain.user.repository;

import com.sparta.ecommerce.domain.user.entity.BalanceHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 잔액 충전 이력 Repository
 */
@Repository
public interface BalanceHistoryRepository extends JpaRepository<BalanceHistory, Long> {

    /**
     * 특정 사용자의 충전 이력 조회 (최신순)
     */
    List<BalanceHistory> findByUserIdOrderByChargedAtDesc(String userId);
}
