package com.atoria.backend.domain.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PresignedUrlRequest(
        @NotBlank(message = "파일명은 필수입니다.")
        @Size(max = 255, message = "파일명은 255자를 초과할 수 없습니다.")
        String fileName,

        @NotBlank(message = "Content-Type은 필수입니다.")
        @Pattern(regexp = "image/[-+.\\w]+|video/[-+.\\w]+|application/pdf",
                message = "지원하지 않는 Content-Type입니다.")
        String contentType
) {
}
