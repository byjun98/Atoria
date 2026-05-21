package com.atoria.backend.domain.chapter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MissionFileSubmitRequest(
        @NotBlank(message = "\uD30C\uC77C URL\uC740 \uD544\uC218\uC785\uB2C8\uB2E4.")
        @Size(max = 500, message = "\uD30C\uC77C URL\uC740 500\uC790\uB97C \uCD08\uACFC\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.")
        String fileUrl,

        @NotBlank(message = "\uD30C\uC77C \uD0C0\uC785\uC740 \uD544\uC218\uC785\uB2C8\uB2E4.")
        @Pattern(regexp = "IMAGE|VIDEO", message = "\uD30C\uC77C \uD0C0\uC785\uC740 IMAGE \uB610\uB294 VIDEO\uB9CC \uAC00\uB2A5\uD569\uB2C8\uB2E4.")
        String type,

        @Pattern(
                regexp = "CURRENT_GPS|PHOTO_EXIF_PLACE|PHOTO_EXIF_AREA|UNKNOWN_PLACE|UNVERIFIED",
                message = "\uC704\uCE58 \uC778\uC99D \uC0C1\uD0DC\uAC00 \uC62C\uBC14\uB974\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."
        )
        String locationVerificationStatus
) {
}
