package com.atoria.backend.domain.story.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiStoryIntroRequest(
        @JsonProperty("people_cnt")
        int peopleCnt,

        @JsonProperty("people_information")
        List<PersonInfo> peopleInformation,

        List<StoryPlace> places
) {

    public record PersonInfo(
            String name,
            int age,
            String tendency
    ) {
    }

    public record StoryPlace(
            @JsonProperty("place_id")
            Long placeId,
            int sequence,
            String name,
            String description,
            String address,
            String category,
            double latitude,
            double longitude
    ) {
    }
}
