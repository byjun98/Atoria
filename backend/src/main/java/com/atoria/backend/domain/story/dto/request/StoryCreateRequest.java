package com.atoria.backend.domain.story.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StoryCreateRequest(
        @NotNull(message = "코스 ID는 필수입니다.")
        Long courseId,

        @NotEmpty(message = "주인공 정보는 1명 이상 필요합니다.")
        @Valid
        List<ProtagonistRequest> protagonists,

        List<Long> placeIds
) {

    public record ProtagonistRequest(
            @NotBlank(message = "주인공 이름은 필수입니다.")
            String name,

            @Min(value = 1, message = "주인공 나이는 1 이상이어야 합니다.")
            int age,

            @NotBlank(message = "주인공 성향은 필수입니다.")
            String tendency
    ) {
    }
}
