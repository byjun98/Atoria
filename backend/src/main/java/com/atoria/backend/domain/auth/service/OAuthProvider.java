package com.atoria.backend.domain.auth.service;

import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import java.util.Arrays;

public enum OAuthProvider {

    GOOGLE(
            "google",
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            "https://www.googleapis.com/oauth2/v2/userinfo",
            "openid email profile"
    ),
    KAKAO(
            "kakao",
            "https://kauth.kakao.com/oauth/authorize",
            "https://kauth.kakao.com/oauth/token",
            "https://kapi.kakao.com/v2/user/me",
            "profile_nickname account_email"
    );

    private final String value;
    private final String authorizationUri;
    private final String tokenUri;
    private final String userInfoUri;
    private final String scope;

    OAuthProvider(String value, String authorizationUri, String tokenUri, String userInfoUri, String scope) {
        this.value = value;
        this.authorizationUri = authorizationUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
        this.scope = scope;
    }

    public static OAuthProvider from(String provider) {
        return Arrays.stream(values())
                .filter(value -> value.value.equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED));
    }

    public String value() {
        return value;
    }

    public String authorizationUri() {
        return authorizationUri;
    }

    public String tokenUri() {
        return tokenUri;
    }

    public String userInfoUri() {
        return userInfoUri;
    }

    public String scope() {
        return scope;
    }
}
