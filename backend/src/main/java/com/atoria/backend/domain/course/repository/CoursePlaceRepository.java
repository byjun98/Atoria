package com.atoria.backend.domain.course.repository;

import com.atoria.backend.domain.course.entity.CoursePlace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoursePlaceRepository extends JpaRepository<CoursePlace, Long> {
}
