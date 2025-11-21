package com.sparta.ecommerce.application.user;


import com.sparta.ecommerce.application.user.dto.UserBalanceResponse;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetUserBalanceUseCase {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserBalanceResponse execute(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return UserBalanceResponse.from(user);
    }
}
