package com.atoria.backend.domain.story.dto.response;

import com.atoria.backend.domain.story.repository.StoryChapterRow;

public record StoryDetailChapterResponse(
        Long chapterId,
        int sequence,
        Long placeId,
        String placeTitle,
        String storyContent,
        boolean isCompleted
) {

    public static StoryDetailChapterResponse from(StoryChapterRow row) {
        return new StoryDetailChapterResponse(
                row.getChapterId(),
                row.getSequence(),
                row.getPlaceId(),
                row.getPlaceTitle(),
                row.getStoryContent(),
                row.getIsCompleted()
        );
    }
}
