package com.sparta.ecommerce.domain.user.repository;

import com.sparta.ecommerce.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 사용자 Repository
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 사용자 ID로 조회
     */
    Optional<User> findByUserId(String userId);

    /**
     * 잔액 원자적 업데이트 (충전)
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 업데이트된 행 수
     */
    @Query("UPDATE User u SET u.balance.amount = u.balance.amount + :amount WHERE u.userId = :userId")
    int updateBalanceByUserId(@Param("userId") String userId, @Param("amount") long amount);
}
/*
public interface UserRepository {
    */
/**
     * 사용자 ID로 사용자 조회
     *//*

    Optional<User> findByUserId(String userId);

    */
/**
     * 사용자 저장 (신규 생성 또는 업데이트)
     *//*

    User save(User user);
}
*/


