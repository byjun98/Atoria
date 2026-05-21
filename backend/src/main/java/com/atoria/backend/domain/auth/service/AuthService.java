package com.atoria.backend.domain.auth.service;

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
import com.atoria.backend.domain.auth.dto.response.OAuthAuthorizationResponse;
import com.atoria.backend.domain.auth.dto.response.PasswordResetTokenValidationResponse;
import com.atoria.backend.domain.auth.dto.response.SignupResponse;

public interface AuthService {

    AvailabilityResponse checkNicknameAvailability(String nickname);

    AvailabilityResponse checkEmailAvailability(String email);

    void sendEmailCode(EmailRequest request);

    EmailVerificationResponse verifyEmailCode(EmailCodeVerifyRequest request);

    SignupResponse signup(SignupRequest request);

    AuthTokenResponse login(LoginRequest request);

    void logout(TokenRefreshRequest request);

    AccessTokenResponse refreshToken(TokenRefreshRequest request);

    void requestPasswordReset(PasswordResetRequest request);

    PasswordResetTokenValidationResponse validatePasswordResetToken(String token);

    void confirmPasswordReset(PasswordResetConfirmRequest request);

    OAuthAuthorizationResponse authorizeOAuth(String provider);

    AuthTokenResponse handleOAuthCallback(String provider, String code, String state);
}
