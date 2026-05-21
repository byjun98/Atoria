package com.atoria.backend.domain.file.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record AiEbookJobResponse(
        boolean success,
        Data data,
        JsonNode error,
        String timestamp
) {

    public record Data(
            @JsonProperty("story_id")
            Long storyId,

            @JsonProperty("ebook_content")
            JsonNode ebookContent
    ) {
    }
}
