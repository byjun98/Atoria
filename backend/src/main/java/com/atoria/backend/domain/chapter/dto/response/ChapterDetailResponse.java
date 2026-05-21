package com.atoria.backend.domain.chapter.dto.response;

import com.atoria.backend.domain.chapter.repository.ChapterDetailRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChapterDetailResponse(
        Long chapterId,
        int sequence,
        boolean isCompleted,
        PlaceResponse place,
        StoryResponse story,
        MissionResponse mission
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ChapterDetailResponse from(ChapterDetailRow row) {
        boolean completed = row.getIsCompleted();
        return new ChapterDetailResponse(
                row.getChapterId(),
                row.getSequence(),
                completed,
                new PlaceResponse(
                        row.getPlaceId(),
                        row.getPlaceTitle(),
                        row.getLatitude(),
                        row.getLongitude()
                ),
                new StoryResponse(parseStoryContent(row.getStoryContent())),
                new MissionResponse(
                        row.getMissionTitle(),
                        row.getMissionDescription(),
                        row.getMissionVerificationHint(),
                        row.getMissionType(),
                        new MissionProgressResponse(
                                completed,
                                row.getFileUrl(),
                                row.getLocationVerificationStatus(),
                                row.getCompletedAt()
                        )
                )
        );
    }

    private static String parseStoryContent(String storyContent) {
        if (storyContent == null || storyContent.isBlank()) {
            return null;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(storyContent);
            JsonNode content = root.get("content");
            if (content != null && !content.isNull()) {
                return content.asText();
            }
            if (root.isTextual()) {
                return root.asText();
            }
        } catch (Exception exception) {
            return storyContent;
        }

        return storyContent;
    }

    public record PlaceResponse(
            Long placeId,
            String title,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record StoryResponse(
            String content
    ) {
    }

    public record MissionResponse(
            String title,
            String description,
            String verificationHint,
            String type,
            MissionProgressResponse progress
    ) {
    }

    public record MissionProgressResponse(
            boolean isCompleted,
            String fileUrl,
            String locationVerificationStatus,
            LocalDateTime completedAt
    ) {
    }
}
