package com.sparta.ecommerce.infrastructure.memory;

import com.sparta.ecommerce.domain.user.User;
import com.sparta.ecommerce.domain.user.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 사용자 Repository 구현체
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 테스트용 초기 데이터
        save(User.builder()
                .userId("U001")
                .name("홍길동")
                .balance(new Balance(2000000L))
                .build());

        save(User.builder()
                .userId("U002")
                .name("김철수")
                .balance(new Balance(1500000L))
                .build());

        save(User.builder()
                .userId("U003")
                .name("박영희")
                .balance(Balance.zero())
                .build());
    }

    @Override
    public Optional<User> findByUserId(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public User save(User user) {
        store.put(user.getUserId(), user);
        return user;
    }
}
