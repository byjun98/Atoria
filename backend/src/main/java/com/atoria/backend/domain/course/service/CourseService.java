package com.atoria.backend.domain.course.service;

import com.atoria.backend.domain.course.dto.response.CourseDetailResponse;
import com.atoria.backend.domain.course.dto.response.CourseListResponse;
import java.util.List;

public interface CourseService {

    List<CourseListResponse> getCourses();

    CourseDetailResponse getCourse(Long courseId);
}
