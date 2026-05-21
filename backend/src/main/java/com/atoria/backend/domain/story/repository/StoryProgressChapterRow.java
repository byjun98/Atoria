package com.atoria.backend.domain.story.repository;

public interface StoryProgressChapterRow {

    Long getChapterId();

    String getPlaceTitle();

    boolean getIsCompleted();

    String getLocationVerificationStatus();
}
