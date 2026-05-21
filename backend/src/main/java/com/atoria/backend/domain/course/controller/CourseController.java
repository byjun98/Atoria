package com.atoria.backend.domain.course.controller;

import com.atoria.backend.domain.course.dto.response.CourseDetailResponse;
import com.atoria.backend.domain.course.dto.response.CourseListResponse;
import com.atoria.backend.domain.course.service.CourseService;
import com.atoria.backend.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ApiResponse<List<CourseListResponse>> getCourses() {
        return ApiResponse.success("코스 목록 조회 성공", courseService.getCourses());
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> getCourse(@PathVariable Long courseId) {
        return ApiResponse.success("코스 상세 조회 성공", courseService.getCourse(courseId));
    }
}
