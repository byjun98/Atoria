package com.atoria.backend.domain.chapter.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ChapterDetailRow {

    Long getChapterId();

    int getSequence();

    boolean getIsCompleted();

    Long getPlaceId();

    String getPlaceTitle();

    BigDecimal getLatitude();

    BigDecimal getLongitude();

    String getStoryContent();

    String getMissionTitle();

    String getMissionDescription();

    String getMissionVerificationHint();

    String getMissionType();

    String getFileUrl();

    String getLocationVerificationStatus();

    LocalDateTime getCompletedAt();
}
