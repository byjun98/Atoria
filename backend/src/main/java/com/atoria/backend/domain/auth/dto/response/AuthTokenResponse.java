package com.atoria.backend.domain.auth.dto.response;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        AuthUserResponse user
) {
}
