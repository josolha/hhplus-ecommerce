package com.sparta.ecommerce.domain.user.repository;

import com.sparta.ecommerce.IntegrationTestBase;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.vo.Balance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * User Repository 통합 테스트
 * Testcontainers를 사용하여 실제 MySQL DB와 JPA 동작 검증
 */
@DisplayName("User Repository 통합 테스트")
public class UserRepositoryTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("User 저장 및 조회 - JPA ID 자동 생성")
    void saveAndFind() {
        // given
        User user = User.builder()
                .name("테스트유저")
                .email("test@test.com")
                .balance(new Balance(10000L))
                .build();

        // when
        User savedUser = userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(savedUser.getUserId()).isNotNull();  // ID 자동 생성 검증

        User foundUser = userRepository.findById(savedUser.getUserId()).get();
        assertThat(foundUser.getName()).isEqualTo("테스트유저");
        assertThat(foundUser.getEmail()).isEqualTo("test@test.com");
        assertThat(foundUser.getBalance().amount()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("User 잔액 충전 - 업데이트 검증")
    void chargeBalance() {
        // given
        User user = User.builder()
                .name("테스트유저")
                .email("charge@test.com")
                .balance(new Balance(10000L))
                .build();
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // when
        User foundUser = userRepository.findById(user.getUserId()).get();
        foundUser.chargeBalance(5000L);  // 5000원 충전
        userRepository.save(foundUser);
        entityManager.flush();
        entityManager.clear();

        // then
        User updatedUser = userRepository.findById(user.getUserId()).get();
        assertThat(updatedUser.getBalance().amount()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("User 잔액 차감 - 업데이트 검증")
    void deductBalance() {
        // given
        User user = User.builder()
                .name("테스트유저")
                .email("deduct@test.com")
                .balance(new Balance(10000L))
                .build();
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // when
        User foundUser = userRepository.findById(user.getUserId()).get();
        foundUser.deductBalance(3000L);  // 3000원 차감
        userRepository.save(foundUser);
        entityManager.flush();
        entityManager.clear();

        // then
        User updatedUser = userRepository.findById(user.getUserId()).get();
        assertThat(updatedUser.getBalance().amount()).isEqualTo(7000L);
    }

    @Test
    @DisplayName("User 이메일 중복 시 에러 - DB 제약조건 검증")
    void duplicateEmail() {
        // given
        User user1 = User.builder()
                .name("유저1")
                .email("duplicate@test.com")
                .balance(new Balance(10000L))
                .build();
        userRepository.save(user1);
        entityManager.flush();

        User user2 = User.builder()
                .name("유저2")
                .email("duplicate@test.com")  // 같은 이메일
                .balance(new Balance(10000L))
                .build();

        // when & then
        assertThatThrownBy(() -> {
            userRepository.save(user2);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("User 조회 시 createdAt/updatedAt 자동 생성 검증")
    void auditFields() {
        // given
        User user = User.builder()
                .name("테스트유저")
                .email("audit@test.com")
                .balance(new Balance(10000L))
                .build();

        // when
        User savedUser = userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // then
        User foundUser = userRepository.findById(savedUser.getUserId()).get();
        assertThat(foundUser.getCreatedAt()).isNotNull();  // @CreatedDate
        assertThat(foundUser.getUpdatedAt()).isNotNull();  // @LastModifiedDate
    }

    @Test
    @DisplayName("User 삭제 후 조회 시 빈 Optional 반환")
    void deleteAndFind() {
        // given
        User user = User.builder()
                .name("삭제될유저")
                .email("delete@test.com")
                .balance(new Balance(10000L))
                .build();
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // when
        userRepository.deleteById(user.getUserId());
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(userRepository.findById(user.getUserId())).isEmpty();
    }
}
