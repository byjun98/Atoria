package com.atoria.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UserDeleteRequest(
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
