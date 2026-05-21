package com.atoria.backend.domain.course.repository;

import com.atoria.backend.domain.course.entity.Course;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("""
            select distinct c
            from Course c
            left join fetch c.coursePlaces cp
            left join fetch cp.place
            order by c.id asc
            """)
    List<Course> findAllWithPlaces();

    @Query("""
            select distinct c
            from Course c
            left join fetch c.coursePlaces cp
            left join fetch cp.place
            where c.id = :courseId
            """)
    Optional<Course> findByIdWithPlaces(@Param("courseId") Long courseId);
}
