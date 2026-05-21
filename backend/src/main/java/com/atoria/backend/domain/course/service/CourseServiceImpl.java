package com.atoria.backend.domain.course.service;

import com.atoria.backend.domain.course.dto.response.CourseDetailResponse;
import com.atoria.backend.domain.course.dto.response.CourseListResponse;
import com.atoria.backend.domain.course.entity.Course;
import com.atoria.backend.domain.course.repository.CourseRepository;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;

    public CourseServiceImpl(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public List<CourseListResponse> getCourses() {
        return courseRepository.findAllWithPlaces().stream()
                .map(CourseListResponse::from)
                .toList();
    }

    @Override
    public CourseDetailResponse getCourse(Long courseId) {
        Course course = courseRepository.findByIdWithPlaces(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        return CourseDetailResponse.from(course);
    }
}
