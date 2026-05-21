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
import com.atoria.backend.domain.auth.dto.response.AuthUserResponse;
import com.atoria.backend.domain.auth.dto.response.AvailabilityResponse;
import com.atoria.backend.domain.auth.dto.response.EmailVerificationResponse;
import com.atoria.backend.domain.auth.dto.response.OAuthAuthorizationResponse;
import com.atoria.backend.domain.auth.dto.response.PasswordResetTokenValidationResponse;
import com.atoria.backend.domain.auth.dto.response.SignupResponse;
import com.atoria.backend.domain.user.entity.User;
import com.atoria.backend.domain.user.repository.UserRepository;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final String FOLLOW_UP_TICKET_MESSAGE = "후속 회원 API 티켓에서 구현 예정입니다.";
    private static final int EMAIL_CODE_BOUND = 1_000_000;
    private static final int PASSWORD_RESET_TOKEN_BYTES = 32;
    private static final long PASSWORD_RESET_TOKEN_VALIDITY_SECONDS = 30 * 60;

    private final UserRepository userRepository;
    private final EmailVerificationCodeStore emailVerificationCodeStore;
    private final EmailVerificationCodeSender emailVerificationCodeSender;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final PasswordResetTokenSender passwordResetTokenSender;
    private final OAuthClientProperties oauthClientProperties;
    private final OAuthClient oauthClient;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(
            UserRepository userRepository,
            EmailVerificationCodeStore emailVerificationCodeStore,
            EmailVerificationCodeSender emailVerificationCodeSender,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenStore refreshTokenStore,
            PasswordResetTokenStore passwordResetTokenStore,
            PasswordResetTokenSender passwordResetTokenSender,
            OAuthClientProperties oauthClientProperties,
            OAuthClient oauthClient,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.emailVerificationCodeStore = emailVerificationCodeStore;
        this.emailVerificationCodeSender = emailVerificationCodeSender;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordResetTokenStore = passwordResetTokenStore;
        this.passwordResetTokenSender = passwordResetTokenSender;
        this.oauthClientProperties = oauthClientProperties;
        this.oauthClient = oauthClient;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AvailabilityResponse checkNicknameAvailability(String nickname) {
        if (userRepository.existsByNicknameAndDeletedFalse(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATE);
        }
        return new AvailabilityResponse(true);
    }

    @Override
    public AvailabilityResponse checkEmailAvailability(String email) {
        if (userRepository.existsByEmailAndDeletedFalse(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
        }
        return new AvailabilityResponse(true);
    }

    @Override
    @Transactional
    public void sendEmailCode(EmailRequest request) {
        checkEmailAvailability(request.email());
        String code = generateEmailCode();
        emailVerificationCodeStore.save(request.email(), code);
        emailVerificationCodeSender.send(request.email(), code);
    }

    @Override
    @Transactional
    public EmailVerificationResponse verifyEmailCode(EmailCodeVerifyRequest request) {
        if (!emailVerificationCodeStore.matches(request.email(), request.code())) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        emailVerificationCodeStore.markVerified(request.email());
        return new EmailVerificationResponse(true);
    }

    @Override
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        checkEmailAvailability(request.email());
        checkNicknameAvailability(request.nickname());
        validatePasswordConfirm(request.password(), request.passwordConfirm());
        validateEmailVerified(request.email(), request.authCode());

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        );
        User savedUser = userRepository.save(user);
        emailVerificationCodeStore.remove(request.email());

        return new SignupResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getNickname());
    }

    @Override
    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedFalse(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);
        refreshTokenStore.save(user.getId(), refreshToken, jwtTokenProvider.getRefreshTokenValiditySeconds());

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new AuthUserResponse(user.getId(), user.getNickname())
        );
    }

    @Override
    @Transactional
    public void logout(TokenRefreshRequest request) {
        Long userId = jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken());
        validateStoredRefreshToken(userId, request.refreshToken());
        refreshTokenStore.delete(userId);
    }

    @Override
    @Transactional
    public AccessTokenResponse refreshToken(TokenRefreshRequest request) {
        Long userId = jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken());
        validateStoredRefreshToken(userId, request.refreshToken());

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        return new AccessTokenResponse(jwtTokenProvider.createAccessToken(user));
    }

    @Override
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmailAndDeletedFalse(request.email())
                .ifPresent(user -> {
                    String token = generatePasswordResetToken();
                    passwordResetTokenStore.save(token, user.getId(), PASSWORD_RESET_TOKEN_VALIDITY_SECONDS);
                    passwordResetTokenSender.send(user.getEmail(), token);
                });
    }

    @Override
    public PasswordResetTokenValidationResponse validatePasswordResetToken(String token) {
        return new PasswordResetTokenValidationResponse(passwordResetTokenStore.findUserId(token).isPresent());
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        validatePasswordConfirm(request.newPassword(), request.newPasswordConfirm());

        Long userId = passwordResetTokenStore.consume(request.token())
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenStore.delete(user.getId());
    }

    @Override
    public OAuthAuthorizationResponse authorizeOAuth(String provider) {
        OAuthProvider oauthProvider = OAuthProvider.from(provider);
        OAuthClientProperties.OAuthClient client = oauthClientProperties.clientOf(oauthProvider);
        validateOAuthClientConfigured(client);

        String authorizationUrl = UriComponentsBuilder.fromUriString(oauthProvider.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", client.clientId())
                .queryParam("redirect_uri", client.redirectUri())
                .queryParam("scope", oauthProvider.scope())
                .queryParam("state", generateOAuthState())
                .build()
                .toUriString();

        return new OAuthAuthorizationResponse(authorizationUrl);
    }

    @Override
    @Transactional
    public AuthTokenResponse handleOAuthCallback(String provider, String code, String state) {
        OAuthProvider oauthProvider = OAuthProvider.from(provider);
        OAuthClientProperties.OAuthClient client = oauthClientProperties.clientOf(oauthProvider);
        validateOAuthClientConfigured(client);

        OAuthUserInfo oauthUserInfo = oauthClient.getUserInfo(oauthProvider, client, code);
        User user = findOrCreateOAuthUser(oauthProvider, oauthUserInfo);

        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);
        refreshTokenStore.save(user.getId(), refreshToken, jwtTokenProvider.getRefreshTokenValiditySeconds());

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new AuthUserResponse(user.getId(), user.getNickname())
        );
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, FOLLOW_UP_TICKET_MESSAGE);
    }

    private void validatePasswordConfirm(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
    }

    private void validateEmailVerified(String email, String authCode) {
        if (!emailVerificationCodeStore.matches(email, authCode)) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }

        if (!emailVerificationCodeStore.isVerified(email)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    private String generateEmailCode() {
        return String.format("%06d", secureRandom.nextInt(EMAIL_CODE_BOUND));
    }

    private String generatePasswordResetToken() {
        byte[] bytes = new byte[PASSWORD_RESET_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateOAuthState() {
        return UUID.randomUUID().toString();
    }

    private void validateOAuthClientConfigured(OAuthClientProperties.OAuthClient client) {
        if (!client.isConfigured()) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED);
        }
    }

    private User findOrCreateOAuthUser(OAuthProvider provider, OAuthUserInfo oauthUserInfo) {
        return userRepository.findByOauthProviderAndOauthIdAndDeletedFalse(provider.value(), oauthUserInfo.oauthId())
                .orElseGet(() -> findByEmailOrCreateOAuthUser(provider, oauthUserInfo));
    }

    private User findByEmailOrCreateOAuthUser(OAuthProvider provider, OAuthUserInfo oauthUserInfo) {
        return userRepository.findByEmailAndDeletedFalse(oauthUserInfo.email())
                .map(user -> {
                    user.linkOAuth(provider.value(), oauthUserInfo.oauthId());
                    return user;
                })
                .orElseGet(() -> createOAuthUser(provider, oauthUserInfo));
    }

    private User createOAuthUser(OAuthProvider provider, OAuthUserInfo oauthUserInfo) {
        String nickname = generateUniqueNickname(oauthUserInfo.nickname());
        String encodedPassword = passwordEncoder.encode(UUID.randomUUID().toString());

        User user = User.createOAuth(
                oauthUserInfo.email(),
                encodedPassword,
                nickname,
                provider.value(),
                oauthUserInfo.oauthId()
        );

        return userRepository.save(user);
    }

    private String generateUniqueNickname(String nickname) {
        String baseNickname = normalizeNickname(nickname);

        if (!userRepository.existsByNicknameAndDeletedFalse(baseNickname)) {
            return baseNickname;
        }

        for (int suffix = 1; suffix < 1_000; suffix++) {
            String candidate = baseNickname + suffix;
            if (!userRepository.existsByNicknameAndDeletedFalse(candidate)) {
                return candidate;
            }
        }

        return "oauthUser" + secureRandom.nextInt(1_000_000);
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "oauthUser";
        }

        return nickname.length() > 40 ? nickname.substring(0, 40) : nickname;
    }

    private void validateStoredRefreshToken(Long userId, String refreshToken) {
        if (!refreshTokenStore.matches(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }
}
