package com.atoria.backend.domain.auth.dto.response;

public record AuthUserResponse(
        Long userId,
        String nickname
) {
}
