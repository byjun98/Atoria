package com.atoria.backend.domain.story.repository;

public interface StoryProgressRow {

    Long getStoryId();

    long getTotalCount();

    long getCompletedCount();
}
