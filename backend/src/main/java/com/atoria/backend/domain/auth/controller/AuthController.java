package com.atoria.backend.domain.auth.controller;

import com.atoria.backend.domain.auth.dto.request.EmailCodeVerifyRequest;
import com.atoria.backend.domain.auth.dto.request.EmailRequest;
import com.atoria.backend.domain.auth.dto.request.LoginRequest;
import com.atoria.backend.domain.auth.dto.request.PasswordResetConfirmRequest;
import com.atoria.backend.domain.auth.dto.request.PasswordResetRequest;
import com.atoria.backend.domain.auth.dto.request.SignupRequest;
import com.atoria.backend.domain.auth.dto.request.TokenRefreshRequest;
import com.atoria.backend.domain.auth.dto.response.AccessTokenResponse;
import com.atoria.backend.domain.auth.dto.response.AuthTokenResponse;
import com.atoria.backend.domain.auth.dto.response.AvailabilityResponse;
import com.atoria.backend.domain.auth.dto.response.EmailVerificationResponse;
import com.atoria.backend.domain.auth.dto.response.PasswordResetTokenValidationResponse;
import com.atoria.backend.domain.auth.dto.response.SignupResponse;
import com.atoria.backend.domain.auth.service.AuthService;
import com.atoria.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/nickname/exists")
    public ApiResponse<AvailabilityResponse> checkNickname(@RequestParam String nickname) {
        return ApiResponse.success("사용 가능한 닉네임입니다.", authService.checkNicknameAvailability(nickname));
    }

    @GetMapping("/email/exists")
    public ApiResponse<AvailabilityResponse> checkEmail(@RequestParam String email) {
        return ApiResponse.success("사용 가능한 이메일입니다.", authService.checkEmailAvailability(email));
    }

    @PostMapping("/email/send-code")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody EmailRequest request) {
        authService.sendEmailCode(request);
        return ApiResponse.success("인증코드가 발송되었습니다.", null);
    }

    @PostMapping("/email/verify-code")
    public ApiResponse<EmailVerificationResponse> verifyEmailCode(@Valid @RequestBody EmailCodeVerifyRequest request) {
        return ApiResponse.success("이메일 인증 완료", authService.verifyEmailCode(request));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.created("회원가입 완료", authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인 성공", authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody TokenRefreshRequest request) {
        authService.logout(request);
        return ApiResponse.success("로그아웃 완료", null);
    }

    @PostMapping("/token/refresh")
    public ApiResponse<AccessTokenResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return ApiResponse.success("토큰 재발급 완료", authService.refreshToken(request));
    }

    @PostMapping("/password/reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ApiResponse.success("비밀번호 재설정 메일 발송", null);
    }

    @GetMapping("/password/reset/validate")
    public ApiResponse<PasswordResetTokenValidationResponse> validatePasswordResetToken(@RequestParam String token) {
        return ApiResponse.success("토큰이 유효합니다.", authService.validatePasswordResetToken(token));
    }

    @PostMapping("/password/reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ApiResponse.success("비밀번호 변경 완료", null);
    }
}
