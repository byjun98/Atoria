package com.atoria.backend.domain.course.dto.response;

import com.atoria.backend.domain.course.entity.Course;
import java.util.List;

public record CourseListResponse(
        Long courseId,
        String title,
        String description,
        List<CoursePlaceSummaryResponse> places
) {

    public static CourseListResponse from(Course course) {
        return new CourseListResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getCoursePlaces().stream()
                        .map(CoursePlaceSummaryResponse::from)
                        .toList()
        );
    }
}
