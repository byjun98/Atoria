package com.atoria.backend.domain.chapter.repository;

public interface EbookChapterRow {

    int getSequence();

    Long getPlaceId();

    String getPlaceName();

    String getPlaceAddress();

    String getMissionTitle();

    String getMissionDescription();

    String getMissionType();

    String getStoryContent();

    String getFileUrl();

    String getChoice();
}
