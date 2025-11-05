package com.sparta.ecommerce.application.user;


import com.sparta.ecommerce.application.user.dto.UserBalanceResponse;
import com.sparta.ecommerce.domain.user.User;
import com.sparta.ecommerce.domain.user.UserRepository;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetUserBalanceUseCase {

    private final UserRepository userRepository;

    public UserBalanceResponse execute(String userId) {

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return UserBalanceResponse.from(user);
    }
}
