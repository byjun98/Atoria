package com.atoria.backend.domain.chapter.dto.response;

import java.time.LocalDateTime;

public record MissionResultSubmitResponse(
        Long storyId,
        Long chapterId,
        boolean isCompleted,
        LocalDateTime completedAt,
        Long nextChapterId
) {
}
