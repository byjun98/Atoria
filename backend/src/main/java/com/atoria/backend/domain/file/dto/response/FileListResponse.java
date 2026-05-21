package com.atoria.backend.domain.file.dto.response;

import com.atoria.backend.domain.file.entity.GeneratedFile;
import java.time.LocalDateTime;

public record FileListResponse(
        String ebookId,
        String title,
        String fileKey,
        String thumbnailKey,
        String status,
        LocalDateTime createdAt
) {

    public static FileListResponse from(GeneratedFile generatedFile) {
        return new FileListResponse(
                generatedFile.getId(),
                generatedFile.getTitle(),
                generatedFile.getFileKey(),
                generatedFile.getThumbnailKey(),
                generatedFile.getStatus(),
                generatedFile.getCreatedAt()
        );
    }
}
