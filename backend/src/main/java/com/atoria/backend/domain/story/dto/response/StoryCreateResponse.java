package com.atoria.backend.domain.story.dto.response;

import com.atoria.backend.domain.chapter.entity.Chapter;
import com.atoria.backend.domain.story.entity.Story;
import java.time.LocalDateTime;
import java.util.List;

public record StoryCreateResponse(
        Long storyId,
        Long courseId,
        String title,
        String status,
        String intro,
        List<StoryCreateChapterResponse> chapters,
        String outro,
        LocalDateTime createdAt
) {

    public static StoryCreateResponse of(Story story, List<Chapter> chapters) {
        return new StoryCreateResponse(
                story.getId(),
                story.getCourse().getId(),
                story.getTitle(),
                story.getStatus(),
                story.getIntro(),
                chapters.stream()
                        .map(StoryCreateChapterResponse::from)
                        .toList(),
                story.getOutro(),
                story.getCreatedAt()
        );
    }
}
