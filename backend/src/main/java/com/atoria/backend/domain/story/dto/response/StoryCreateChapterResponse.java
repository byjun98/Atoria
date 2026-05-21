package com.atoria.backend.domain.story.dto.response;

import com.atoria.backend.domain.chapter.entity.Chapter;

public record StoryCreateChapterResponse(
        Long chapterId,
        int sequence,
        Long placeId,
        String placeTitle,
        boolean isCompleted
) {

    public static StoryCreateChapterResponse from(Chapter chapter) {
        return new StoryCreateChapterResponse(
                chapter.getId(),
                chapter.getSequence(),
                chapter.getCoursePlace().getPlace().getId(),
                chapter.getCoursePlace().getPlace().getTitle(),
                false
        );
    }
}
