package com.sparta.ecommerce.presentation.controller.auth;

import com.sparta.ecommerce.application.auth.dto.LoginUserRequest;
import com.sparta.ecommerce.application.auth.dto.LoginUserResponse;
import com.sparta.ecommerce.application.auth.dto.RefreshAndAccessTokenResponse;
import com.sparta.ecommerce.application.auth.dto.RefreshTokenRequest;
import com.sparta.ecommerce.application.auth.dto.RegisterUserRequest;
import com.sparta.ecommerce.application.auth.usecase.LoginUserUseCase;
import com.sparta.ecommerce.application.auth.usecase.LogoutUserUseCase;
import com.sparta.ecommerce.application.auth.usecase.RefreshAccessTokenUseCase;
import com.sparta.ecommerce.application.auth.usecase.RegisterUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 API 컨트롤러
 * - 회원가입, 로그인, 로그아웃, 토큰 재발급
 */
@Tag(name = "인증", description = "회원가입/로그인/로그아웃/토큰재발급")
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final RefreshAccessTokenUseCase refreshAccessTokenUseCase;
    private final LogoutUserUseCase logoutUserUseCase;

    @Operation(
            summary = "회원가입",
            description = "새로운 사용자를 등록합니다. 이메일은 고유해야 하며, 비밀번호는 8-20자 사이여야 합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "message": "회원가입이 완료되었습니다."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이메일 중복 또는 유효성 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "AUTH001",
                                      "message": "이미 사용 중인 이메일입니다."
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody RegisterUserRequest request) {
        registerUserUseCase.execute(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", "회원가입이 완료되었습니다."));
    }

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 로그인하여 Access Token과 Refresh Token을 발급받습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginUserResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                                      "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 - 이메일 또는 비밀번호 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "AUTH002",
                                      "message": "이메일 또는 비밀번호가 올바르지 않습니다."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "소셜 로그인 계정",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "AUTH003",
                                      "message": "소셜 로그인 계정입니다."
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginUserResponse> login(@Valid @RequestBody LoginUserRequest request) {
        return ResponseEntity.ok(loginUserUseCase.execute(request));
    }

    @Operation(
            summary = "Access Token 재발급",
            description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급합니다. (Refresh Token Rotation 적용)"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 재발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshAndAccessTokenResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                                      "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 Refresh Token",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "AUTH004",
                                      "message": "유효하지 않은 Refresh Token입니다."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그아웃된 토큰 또는 탈취 의심",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "AUTH005",
                                      "message": "탈취 의심으로 모든 세션이 종료되었습니다. 다시 로그인해주세요."
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshAndAccessTokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        RefreshAndAccessTokenResponse response = refreshAccessTokenUseCase.execute(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "로그아웃",
            description = "사용자가 로그아웃합니다. Redis에서 Refresh Token이 삭제되며, 해당 사용자의 모든 세션이 무효화됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "message": "로그아웃되었습니다."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "COMMON002",
                                      "message": "인증이 필요합니다."
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication) {
        // 인증된 사용자의 userId 추출
        String userId = authentication.getName();
        // 로그아웃 실행 (Redis에서 Refresh Token 삭제)
        logoutUserUseCase.execute(userId);
        return ResponseEntity.ok(Map.of("message", "로그아웃되었습니다."));
    }

}
