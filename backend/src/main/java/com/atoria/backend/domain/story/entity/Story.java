package com.atoria.backend.domain.story.entity;

import com.atoria.backend.domain.course.entity.Course;
import com.atoria.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "stories")
public class Story {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "story_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "protagonist_info", columnDefinition = "jsonb")
    @ColumnTransformer(read = "protagonist_info::text", write = "?::jsonb")
    private String protagonistInfo;

    @Column(columnDefinition = "TEXT")
    private String intro;

    @Column(columnDefinition = "TEXT")
    private String outro;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Story() {

    }

    private Story(User user, Course course, String title, String protagonistInfo, String intro, String outro) {
        this.user = user;
        this.course = course;
        this.title = title;
        this.status = STATUS_IN_PROGRESS;
        this.protagonistInfo = protagonistInfo;
        this.intro = intro;
        this.outro = outro;
    }

    public static Story create(User user, Course course, String title, String protagonistInfo, String intro, String outro) {
        return new Story(user, course, title, protagonistInfo, intro, outro);
    }

    public void updateProgressStatus(boolean completed) {
        this.status = completed ? STATUS_COMPLETED : STATUS_IN_PROGRESS;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Course getCourse() {
        return course;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getProtagonistInfo() {
        return protagonistInfo;
    }

    public String getIntro() {
        return intro;
    }

    public String getOutro() {
        return outro;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
