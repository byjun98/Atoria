package com.atoria.backend.domain.chapter.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_chapter_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "chapter_id"})
)
public class UserChapterProgress {

    public static final String LOCATION_VERIFICATION_UNVERIFIED = "UNVERIFIED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_chapter_progress_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(name = "is_completed", nullable = false)
    private boolean completed;

    private String choice;

    @Column(name = "input_text", columnDefinition = "TEXT")
    private String inputText;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "location_verification_status", length = 30)
    private String locationVerificationStatus;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserChapterProgress() {
    }

    private UserChapterProgress(User user, Chapter chapter) {
        this.user = user;
        this.chapter = chapter;
        this.completed = false;
        this.startedAt = LocalDateTime.now();
    }

    public static UserChapterProgress create(User user, Chapter chapter) {
        return new UserChapterProgress(user, chapter);
    }

    public void submitResult(boolean completed) {
        this.completed = completed;
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
        this.completedAt = completed ? LocalDateTime.now() : null;
    }

    public LocalDateTime submitFile(String fileUrl, String locationVerificationStatus) {
        LocalDateTime now = LocalDateTime.now();
        this.fileUrl = fileUrl;
        this.locationVerificationStatus = locationVerificationStatus;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.updatedAt = now;
        return now;
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

    public boolean isCompleted() {
        return completed;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getLocationVerificationStatus() {
        return locationVerificationStatus;
    }
}
