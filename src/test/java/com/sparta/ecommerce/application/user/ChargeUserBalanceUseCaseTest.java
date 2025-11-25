package com.sparta.ecommerce.application.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.sparta.ecommerce.application.user.dto.ChargeBalanceRequest;
import com.sparta.ecommerce.application.user.dto.ChargeBalanceResponse;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.BalanceHistoryRepository;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import com.sparta.ecommerce.domain.user.vo.Balance;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 잔액 충전 UseCase 테스트")
class ChargeUserBalanceUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BalanceHistoryRepository balanceHistoryRepository;

    @InjectMocks
    private ChargeUserBalanceUseCase chargeUserBalanceUseCase;

    @Test
    @DisplayName("사용자의 잔액을 정상적으로 충전한다")
    void 잔액_충전_성공() {
        // given
        String userId = "U001";
        long previousBalance = 100000L;
        long chargeAmount = 50000L;

        User user = User.builder()
                .userId(userId)
                .name("홍길동")
                .balance(new Balance(previousBalance))
                .build();

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        ChargeBalanceRequest request = new ChargeBalanceRequest(chargeAmount);

        // when
        ChargeBalanceResponse response = chargeUserBalanceUseCase.execute(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.previousBalance()).isEqualTo(previousBalance);
        assertThat(response.chargedAmount()).isEqualTo(chargeAmount);
        assertThat(response.currentBalance()).isEqualTo(previousBalance + chargeAmount);

        // User 저장 검증
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getBalance().amount()).isEqualTo(previousBalance + chargeAmount);
    }

    @Test
    @DisplayName("잔액이 0원인 사용자도 충전이 가능하다")
    void 잔액_0원에서_충전() {
        // given
        String userId = "U002";
        long chargeAmount = 100000L;

        User user = User.builder()
                .userId(userId)
                .name("김철수")
                .balance(Balance.zero())
                .build();

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        ChargeBalanceRequest request = new ChargeBalanceRequest(chargeAmount);

        // when
        ChargeBalanceResponse response = chargeUserBalanceUseCase.execute(userId, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.previousBalance()).isEqualTo(0L);
        assertThat(response.chargedAmount()).isEqualTo(chargeAmount);
        assertThat(response.currentBalance()).isEqualTo(chargeAmount);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("충전 금액이 0 이하인 경우 예외가 발생한다")
    void 충전_금액_0_이하_예외() {
        // given
        String userId = "U001";
        User user = User.builder()
                .userId(userId)
                .name("홍길동")
                .balance(new Balance(100000))
                .build();

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        ChargeBalanceRequest request = new ChargeBalanceRequest(0L);

        // when & then
        assertThatThrownBy(() -> chargeUserBalanceUseCase.execute(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("충전 금액은 0보다 커야 합니다");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("음수 금액으로 충전 시도 시 예외가 발생한다")
    void 충전_금액_음수_예외() {
        // given
        String userId = "U001";
        User user = User.builder()
                .userId(userId)
                .name("홍길동")
                .balance(new Balance(100000))
                .build();

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        ChargeBalanceRequest request = new ChargeBalanceRequest(-10000L);

        // when & then
        assertThatThrownBy(() -> chargeUserBalanceUseCase.execute(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("충전 금액은 0보다 커야 합니다");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자에게 충전 시도 시 UserNotFoundException이 발생한다")
    void 사용자_없음_예외() {
        // given
        String userId = "INVALID_USER";
        given(userRepository.findById(userId))
                .willReturn(Optional.empty());

        ChargeBalanceRequest request = new ChargeBalanceRequest(10000L);

        // when & then
        assertThatThrownBy(() -> chargeUserBalanceUseCase.execute(userId, request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("큰 금액 충전도 정상적으로 처리된다")
    void 큰_금액_충전() {
        // given
        String userId = "U003";
        long previousBalance = 1000000L;
        long chargeAmount = 10000000L; // 천만원

        User user = User.builder()
                .userId(userId)
                .name("박영희")
                .balance(new Balance(previousBalance))
                .build();

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        ChargeBalanceRequest request = new ChargeBalanceRequest(chargeAmount);

        // when
        ChargeBalanceResponse response = chargeUserBalanceUseCase.execute(userId, request);

        // then
        assertThat(response.currentBalance()).isEqualTo(previousBalance + chargeAmount);
        assertThat(response.currentBalance()).isEqualTo(11000000L);

        verify(userRepository, times(1)).save(any(User.class));
    }
}
