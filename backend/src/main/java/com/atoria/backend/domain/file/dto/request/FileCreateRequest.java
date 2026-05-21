package com.atoria.backend.domain.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record FileCreateRequest(
        @NotNull(message = "코스 ID는 필수입니다.")
        Long courseId,

        @NotNull(message = "스토리 ID는 필수입니다.")
        Long storyId,

        @NotBlank(message = "결과물 제목은 필수입니다.")
        @Size(max = 255, message = "결과물 제목은 255자를 초과할 수 없습니다.")
        String title,

        Map<String, Object> options
) {
}
