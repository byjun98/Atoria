package com.atoria.backend.domain.chapter.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MissionResultSubmitRequest(
        @NotNull(message = "미션 결과는 필수입니다.")
        @Valid
        MissionResult result
) {

    public record MissionResult(
            boolean isCompleted
    ) {
    }
}
