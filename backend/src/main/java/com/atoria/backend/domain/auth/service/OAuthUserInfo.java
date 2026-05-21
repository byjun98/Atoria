package com.atoria.backend.domain.auth.service;

public record OAuthUserInfo(
        String oauthId,
        String email,
        String nickname
) {
}
