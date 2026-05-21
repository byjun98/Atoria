package com.atoria.backend.domain.file.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiEbookJobRequest(
        @JsonProperty("story_id")
        Long storyId,

        @JsonProperty("user_id")
        Long userId,

        StoryBlock story,

        List<Chapter> chapters
) {

    public record StoryBlock(
            String title,
            String intro,
            String outro,

            @JsonProperty("protagonist_info")
            ProtagonistInfo protagonistInfo
    ) {
    }

    public record ProtagonistInfo(
            @JsonProperty("people_cnt")
            int peopleCnt,

            @JsonProperty("people_information")
            List<PersonInfo> peopleInformation
    ) {
    }

    public record PersonInfo(
            String name,
            int age,
            String tendency
    ) {
    }

    public record Chapter(
            int sequence,

            @JsonProperty("place_id")
            Long placeId,

            @JsonProperty("place_name")
            String placeName,

            @JsonProperty("place_address")
            String placeAddress,

            @JsonProperty("mission_title")
            String missionTitle,

            @JsonProperty("mission_description")
            String missionDescription,

            @JsonProperty("mission_type")
            String missionType,

            @JsonProperty("story_content")
            String storyContent,

            @JsonProperty("user_result")
            UserResult userResult
    ) {
    }

    public record UserResult(
            @JsonProperty("image_url")
            String imageUrl,

            String choice
    ) {
    }
}
