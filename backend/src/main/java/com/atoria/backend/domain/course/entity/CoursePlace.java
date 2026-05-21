package com.atoria.backend.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_places")
public class CoursePlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_place_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false)
    private int sequence;

    protected CoursePlace() {
    }

    private CoursePlace(Course course, Place place, int sequence) {
        this.course = course;
        this.place = place;
        this.sequence = sequence;
    }

    public static CoursePlace create(Course course, Place place, int sequence) {
        return new CoursePlace(course, place, sequence);
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public Place getPlace() {
        return place;
    }

    public int getSequence() {
        return sequence;
    }
}
