package com.atoria.backend.domain.story.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiStoryIntroResponse(
        String intro,
        List<Mission> missions,
        String outro
) {

    public record Mission(
            int sequence,
            String title,
            String description,
            @JsonProperty("verification_hint")
            String verificationHint,
            String type,
            String story
    ) {
    }
}
