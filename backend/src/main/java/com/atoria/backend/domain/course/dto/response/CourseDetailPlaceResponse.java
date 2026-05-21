package com.atoria.backend.domain.course.dto.response;

import com.atoria.backend.domain.course.entity.CoursePlace;
import java.math.BigDecimal;

public record CourseDetailPlaceResponse(
        Long placeId,
        String title,
        BigDecimal latitude,
        BigDecimal longitude
) {

    public static CourseDetailPlaceResponse from(CoursePlace coursePlace) {
        return new CourseDetailPlaceResponse(
                coursePlace.getPlace().getId(),
                coursePlace.getPlace().getTitle(),
                coursePlace.getPlace().getLatitude(),
                coursePlace.getPlace().getLongitude()
        );
    }
}
