package com.atoria.backend.domain.file.dto.response;

public record PresignedUrlResponse(
        String presignedUrl,
        String fileKey,
        String publicUrl
) {
}
