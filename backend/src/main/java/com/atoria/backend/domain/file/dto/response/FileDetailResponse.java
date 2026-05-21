package com.atoria.backend.domain.file.dto.response;

import com.atoria.backend.domain.file.entity.GeneratedFile;
import java.time.LocalDateTime;

public record FileDetailResponse(
        String ebookId,
        String title,
        String fileKey,
        String thumbnailKey,
        String status,
        LocalDateTime createdAt,
        Object metadata
) {

    public static FileDetailResponse of(GeneratedFile generatedFile, Object metadata) {
        return new FileDetailResponse(
                generatedFile.getId(),
                generatedFile.getTitle(),
                generatedFile.getFileKey(),
                generatedFile.getThumbnailKey(),
                generatedFile.getStatus(),
                generatedFile.getCreatedAt(),
                metadata
        );
    }
}
