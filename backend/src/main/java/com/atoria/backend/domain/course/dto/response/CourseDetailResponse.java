package com.atoria.backend.domain.course.dto.response;

import com.atoria.backend.domain.course.entity.Course;
import java.util.List;

public record CourseDetailResponse(
        Long courseId,
        String title,
        String description,
        List<CourseDetailPlaceResponse> places
) {

    public static CourseDetailResponse from(Course course) {
        return new CourseDetailResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getCoursePlaces().stream()
                        .map(CourseDetailPlaceResponse::from)
                        .toList()
        );
    }
}
