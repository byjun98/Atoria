package com.atoria.backend.domain.user.dto.response;

public record UserProfileResponse(
        Long userId,
        String email,
        String nickname
) {
}
