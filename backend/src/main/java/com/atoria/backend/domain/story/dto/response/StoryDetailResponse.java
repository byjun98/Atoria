package com.atoria.backend.domain.story.dto.response;

import com.atoria.backend.domain.story.entity.Story;
import com.atoria.backend.domain.story.repository.StoryChapterRow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;

public record StoryDetailResponse(
        Long storyId,
        Long courseId,
        String title,
        String status,
        String intro,
        String outro,
        List<ProtagonistResponse> protagonists,
        List<StoryDetailChapterResponse> chapters,
        LocalDateTime createdAt
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<ProtagonistResponse>> PROTAGONIST_TYPE = new TypeReference<>() {
    };

    public static StoryDetailResponse of(Story story, List<StoryChapterRow> chapterRows) {
        return new StoryDetailResponse(
                story.getId(),
                story.getCourse().getId(),
                story.getTitle(),
                story.getStatus(),
                story.getIntro(),
                story.getOutro(),
                parseProtagonists(story.getProtagonistInfo()),
                chapterRows.stream()
                        .map(StoryDetailChapterResponse::from)
                        .toList(),
                story.getCreatedAt()
        );
    }

    private static List<ProtagonistResponse> parseProtagonists(String protagonistInfo) {
        if (protagonistInfo == null || protagonistInfo.isBlank()) {
            return List.of();
        }

        try {
            return OBJECT_MAPPER.readValue(protagonistInfo, PROTAGONIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    public record ProtagonistResponse(
            String name,
            int age,
            String tendency
    ) {
    }
}
