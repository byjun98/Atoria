package com.atoria.backend.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OAuthClientProperties {

    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private final String kakaoClientId;
    private final String kakaoClientSecret;
    private final String kakaoRedirectUri;

    public OAuthClientProperties(
            @Value("${app.oauth.google.client-id:}") String googleClientId,
            @Value("${app.oauth.google.client-secret:}") String googleClientSecret,
            @Value("${app.oauth.google.redirect-uri:}") String googleRedirectUri,
            @Value("${app.oauth.kakao.client-id:}") String kakaoClientId,
            @Value("${app.oauth.kakao.client-secret:}") String kakaoClientSecret,
            @Value("${app.oauth.kakao.redirect-uri:}") String kakaoRedirectUri
    ) {
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = googleRedirectUri;
        this.kakaoClientId = kakaoClientId;
        this.kakaoClientSecret = kakaoClientSecret;
        this.kakaoRedirectUri = kakaoRedirectUri;
    }

    public OAuthClient clientOf(OAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> new OAuthClient(googleClientId, googleClientSecret, googleRedirectUri);
            case KAKAO -> new OAuthClient(kakaoClientId, kakaoClientSecret, kakaoRedirectUri);
        };
    }

    public record OAuthClient(
            String clientId,
            String clientSecret,
            String redirectUri
    ) {
        public boolean isConfigured() {
            return hasText(clientId) && hasText(redirectUri);
        }

        public boolean hasClientSecret() {
            return hasText(clientSecret);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
