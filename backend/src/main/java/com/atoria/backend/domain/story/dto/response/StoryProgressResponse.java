package com.atoria.backend.domain.story.dto.response;

import com.atoria.backend.domain.story.repository.StoryProgressChapterRow;
import com.atoria.backend.domain.story.repository.StoryProgressRow;
import java.util.List;

public record StoryProgressResponse(
        Long storyId,
        long totalCount,
        long completedCount,
        double progressRate,
        List<StoryProgressChapterResponse> chapters
) {

    public static StoryProgressResponse of(StoryProgressRow progressRow, List<StoryProgressChapterRow> chapterRows) {
        long totalCount = progressRow.getTotalCount();
        long completedCount = progressRow.getCompletedCount();
        double progressRate = totalCount == 0 ? 0.0 : (double) completedCount / totalCount;

        return new StoryProgressResponse(
                progressRow.getStoryId(),
                totalCount,
                completedCount,
                progressRate,
                chapterRows.stream()
                        .map(StoryProgressChapterResponse::from)
                        .toList()
        );
    }

    public record StoryProgressChapterResponse(
            Long chapterId,
            String placeTitle,
            boolean isCompleted,
            String locationVerificationStatus
    ) {

        public static StoryProgressChapterResponse from(StoryProgressChapterRow row) {
            return new StoryProgressChapterResponse(
                    row.getChapterId(),
                    row.getPlaceTitle(),
                    row.getIsCompleted(),
                    row.getLocationVerificationStatus()
            );
        }
    }
}
