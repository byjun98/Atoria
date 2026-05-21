package com.atoria.backend.domain.course.dto.response;

import com.atoria.backend.domain.course.entity.CoursePlace;

public record CoursePlaceSummaryResponse(
        Long placeId,
        String title
) {

    public static CoursePlaceSummaryResponse from(CoursePlace coursePlace) {
        return new CoursePlaceSummaryResponse(
                coursePlace.getPlace().getId(),
                coursePlace.getPlace().getTitle()
        );
    }
}
