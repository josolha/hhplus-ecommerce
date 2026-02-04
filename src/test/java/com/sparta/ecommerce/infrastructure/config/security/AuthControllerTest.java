package com.sparta.ecommerce.infrastructure.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ecommerce.application.auth.dto.RegisterUserRequest;
import com.sparta.ecommerce.application.auth.usecase.RegisterUserUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 컨트롤러 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("인증 컨트롤러 테스트")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RegisterUserUseCase registerUserUseCase;

    @Test
    @DisplayName("POST /api/auth/signup - 정상적인 회원가입 요청이 성공한다")
    void 회원가입_성공() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                "test@example.com",
                "password123",
                "홍길동",
                "010-1234-5678"
        );

        doNothing().when(registerUserUseCase).execute(any(RegisterUserRequest.class));

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated());

        verify(registerUserUseCase, times(1)).execute(any(RegisterUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이메일 형식이 올바르지 않으면 400 에러를 반환한다")
    void 회원가입_실패_이메일_형식_오류() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                "invalid-email",
                "password123",
                "홍길동",
                "010-1234-5678"
        );

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).execute(any(RegisterUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 비밀번호가 8자 미만이면 400 에러를 반환한다")
    void 회원가입_실패_비밀번호_짧음() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                "test@example.com",
                "short",
                "홍길동",
                "010-1234-5678"
        );

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).execute(any(RegisterUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 비밀번호가 20자를 초과하면 400 에러를 반환한다")
    void 회원가입_실패_비밀번호_너무_김() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                "test@example.com",
                "verylongpassword12345678901",
                "홍길동",
                "010-1234-5678"
        );

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).execute(any(RegisterUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이메일이 누락되면 400 에러를 반환한다")
    void 회원가입_실패_이메일_누락() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                null,
                "password123",
                "홍길동",
                "010-1234-5678"
        );

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).execute(any(RegisterUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 비밀번호가 누락되면 400 에러를 반환한다")
    void 회원가입_실패_비밀번호_누락() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                "test@example.com",
                null,
                "홍길동",
                "010-1234-5678"
        );

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).execute(any(RegisterUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이름이 누락되면 400 에러를 반환한다")
    void 회원가입_실패_이름_누락() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                "test@example.com",
                "password123",
                null,
                "010-1234-5678"
        );

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).execute(any(RegisterUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이미 존재하는 이메일로 가입 시도하면 예외가 발생한다")
    void 회원가입_실패_이메일_중복() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                "test@example.com",
                "password123",
                "홍길동",
                "010-1234-5678"
        );

        willThrow(new IllegalArgumentException("이미 사용 중인 이메일 입니다."))
                .given(registerUserUseCase).execute(any(RegisterUserRequest.class));

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(registerUserUseCase, times(1)).execute(any(RegisterUserRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 전화번호는 선택사항이므로 누락되어도 성공한다")
    void 회원가입_성공_전화번호_누락() throws Exception {
        // given
        RegisterUserRequest request = new RegisterUserRequest(
                "test@example.com",
                "password123",
                "홍길동",
                null
        );

        doNothing().when(registerUserUseCase).execute(any(RegisterUserRequest.class));

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated());

        verify(registerUserUseCase, times(1)).execute(any(RegisterUserRequest.class));
    }
}
