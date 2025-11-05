package com.sparta.ecommerce.application.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import com.sparta.ecommerce.application.user.dto.UserBalanceResponse;
import com.sparta.ecommerce.domain.user.User;
import com.sparta.ecommerce.domain.user.UserRepository;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import com.sparta.ecommerce.domain.user.vo.Balance;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 잔액 조회 UseCase 테스트")
class GetUserBalanceUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetUserBalanceUseCase getUserBalanceUseCase;

    @Test
    @DisplayName("사용자의 잔액을 정상적으로 조회한다")
    void 사용자_잔액_조회_성공() {
        // given
        String userId = "U001";
        User user = User.builder()
                .userId("U001")
                .name("홍길동")
                .balance(new Balance(100000))
                .build();

        given(userRepository.findByUserId(userId))
                .willReturn(Optional.of(user));

        // when
        UserBalanceResponse response = getUserBalanceUseCase.execute(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("U001");
        assertThat(response.balance()).isEqualTo(100000L);

        verify(userRepository, times(1)).findByUserId(userId);
    }

    @Test
    @DisplayName("잔액이 0원인 사용자도 정상 조회된다")
    void 잔액_0원_사용자_조회() {
        // given
        String userId = "U002";
        User user = User.builder()
                .userId("U002")
                .name("김철수")
                .balance(Balance.zero())  // 0원
                .build();

        given(userRepository.findByUserId(userId))
                .willReturn(Optional.of(user));

        // when
        UserBalanceResponse response = getUserBalanceUseCase.execute(userId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("U002");
        assertThat(response.balance()).isEqualTo(0L);

        verify(userRepository, times(1)).findByUserId(userId);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로 조회하면 UserNotFoundException을 던진다")
    void 사용자_없음_예외() {
        // given
        String userId = "INVALID_USER";
        given(userRepository.findByUserId(userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> getUserBalanceUseCase.execute(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verify(userRepository, times(1)).findByUserId(userId);
    }
}
