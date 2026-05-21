package com.atoria.backend.domain.story.dto.response;

import com.atoria.backend.domain.story.repository.StoryListRow;
import java.time.LocalDateTime;

public record StoryListResponse(
        Long storyId,
        Long courseId,
        String title,
        String status,
        String thumbnailUrl,
        long completedCount,
        long totalCount,
        LocalDateTime createdAt
) {

    public static StoryListResponse from(StoryListRow row) {
        return new StoryListResponse(
                row.getStoryId(),
                row.getCourseId(),
                row.getTitle(),
                row.getStatus(),
                row.getThumbnailUrl(),
                row.getCompletedCount(),
                row.getTotalCount(),
                row.getCreatedAt()
        );
    }
}
