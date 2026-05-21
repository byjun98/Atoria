package com.atoria.backend.domain.story.repository;

public interface StoryChapterRow {

    Long getChapterId();

    int getSequence();

    Long getPlaceId();

    String getPlaceTitle();

    String getStoryContent();

    boolean getIsCompleted();
}
