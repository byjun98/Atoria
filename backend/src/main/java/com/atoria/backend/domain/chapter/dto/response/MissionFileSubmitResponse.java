package com.atoria.backend.domain.chapter.dto.response;

import java.time.LocalDateTime;

public record MissionFileSubmitResponse(
        Long storyId,
        Long chapterId,
        String fileUrl,
        String type,
        String locationVerificationStatus,
        LocalDateTime uploadedAt
) {
}
