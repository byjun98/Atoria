package com.atoria.backend.domain.chapter.entity;

import com.atoria.backend.domain.course.entity.CoursePlace;
import com.atoria.backend.domain.story.entity.Story;
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
@Table(name = "chapters")
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chapter_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_place_id", nullable = false)
    private CoursePlace coursePlace;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "mission_title", length = 100)
    private String missionTitle;

    @Column(name = "mission_description", columnDefinition = "TEXT")
    private String missionDescription;

    @Column(name = "mission_verification_hint", columnDefinition = "TEXT")
    private String missionVerificationHint;

    @Column(name = "mission_type", length = 20)
    private String missionType;

    @Column(name = "story_content", columnDefinition = "jsonb")
    @ColumnTransformer(read = "story_content::text", write = "?::jsonb")
    private String storyContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Chapter() {
    }

    private Chapter(
            Story story,
            CoursePlace coursePlace,
            int sequence,
            String missionTitle,
            String missionDescription,
            String missionVerificationHint,
            String missionType,
            String storyContent
    ) {
        this.story = story;
        this.coursePlace = coursePlace;
        this.sequence = sequence;
        this.missionTitle = missionTitle;
        this.missionDescription = missionDescription;
        this.missionVerificationHint = missionVerificationHint;
        this.missionType = missionType;
        this.storyContent = storyContent;
    }

    public static Chapter create(
            Story story,
            CoursePlace coursePlace,
            int sequence,
            String missionTitle,
            String missionDescription,
            String missionVerificationHint,
            String missionType,
            String storyContent
    ) {
        return new Chapter(
                story,
                coursePlace,
                sequence,
                missionTitle,
                missionDescription,
                missionVerificationHint,
                missionType,
                storyContent
        );
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

    public Story getStory() {
        return story;
    }

    public CoursePlace getCoursePlace() {
        return coursePlace;
    }

    public int getSequence() {
        return sequence;
    }

    public String getMissionTitle() {
        return missionTitle;
    }

    public String getMissionDescription() {
        return missionDescription;
    }

    public String getMissionVerificationHint() {
        return missionVerificationHint;
    }

    public String getMissionType() {
        return missionType;
    }

    public String getStoryContent() {
        return storyContent;
    }
}
