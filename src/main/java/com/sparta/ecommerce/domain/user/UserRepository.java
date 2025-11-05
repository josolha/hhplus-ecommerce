package com.sparta.ecommerce.domain.user;

import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 사용자 Repository
 */
@Repository
public interface UserRepository {
    /**
     * 사용자 ID로 사용자 조회
     */
    Optional<User> findByUserId(String userId);

    /**
     * 사용자 저장 (신규 생성 또는 업데이트)
     */
    User save(User user);
}
