package com.sparta.ecommerce.application.auth.usecase;

import com.sparta.ecommerce.application.auth.dto.RegisterUserRequest;
import com.sparta.ecommerce.domain.user.entity.Provider;
import com.sparta.ecommerce.domain.user.entity.Role;
import com.sparta.ecommerce.domain.user.entity.User;
import com.sparta.ecommerce.domain.user.repository.UserRepository;
import com.sparta.ecommerce.domain.user.vo.Balance;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

   private final UserRepository userRepository;
   private final PasswordEncoder passwordEncoder;

   public void execute(RegisterUserRequest request) {

      if(userRepository.existsByEmail(request.email())){
         throw new IllegalArgumentException("이미 사용 중인 이메일 입니다.");
      }
      String encoded = passwordEncoder.encode(request.password());

      User user = User.builder()
              .email(request.email())
              .password(encoded)
              .name(request.name())
              .phoneNumber(request.phoneNumber())
              .role(Role.USER)
              .provider(Provider.LOCAL)
              .balance(Balance.zero())
              .build();

      userRepository.save(user);
   }
}
