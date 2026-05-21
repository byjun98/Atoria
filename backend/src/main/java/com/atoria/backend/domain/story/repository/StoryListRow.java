package com.atoria.backend.domain.story.repository;

import java.time.LocalDateTime;

public interface StoryListRow {

    Long getStoryId();

    Long getCourseId();

    String getTitle();

    String getStatus();

    String getThumbnailUrl();

    long getCompletedCount();

    long getTotalCount();

    LocalDateTime getCreatedAt();
}
