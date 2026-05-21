package com.atoria.backend.domain.file.entity;

import com.atoria.backend.domain.course.entity.Course;
import com.atoria.backend.domain.story.entity.Story;
import com.atoria.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "generated_files")
public class GeneratedFile {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "file_id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "file_key", length = 500)
    private String fileKey;

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(read = "options::text", write = "?::jsonb")
    private String options;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(read = "metadata::text", write = "?::jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected GeneratedFile() {
    }

    private GeneratedFile(User user, Course course, Story story, String title, String options) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.course = course;
        this.story = story;
        this.title = title;
        this.status = STATUS_PROCESSING;
        this.options = options;
    }

    public static GeneratedFile create(User user, Course course, Story story, String title, String options) {
        return new GeneratedFile(user, course, story, title, options);
    }

    public void complete(String metadata) {
        this.status = STATUS_COMPLETED;
        this.metadata = metadata;
    }

    public void fail(String metadata) {
        this.status = STATUS_FAILED;
        this.metadata = metadata;
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

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Story getStory() {
        return story;
    }

    public String getTitle() {
        return title;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getThumbnailKey() {
        return thumbnailKey;
    }

    public String getStatus() {
        return status;
    }

    public String getMetadata() {
        return metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
