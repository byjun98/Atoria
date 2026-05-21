package com.atoria.backend.domain.chapter.service;

import com.atoria.backend.domain.chapter.dto.request.MissionFileSubmitRequest;
import com.atoria.backend.domain.chapter.dto.request.MissionResultSubmitRequest;
import com.atoria.backend.domain.chapter.dto.response.ChapterDetailResponse;
import com.atoria.backend.domain.chapter.dto.response.MissionFileSubmitResponse;
import com.atoria.backend.domain.chapter.dto.response.MissionResultSubmitResponse;

public interface ChapterService {

    ChapterDetailResponse getChapter(Long userId, Long storyId, Long chapterId);

    MissionResultSubmitResponse submitMissionResult(
            Long userId,
            Long storyId,
            Long chapterId,
            MissionResultSubmitRequest request
    );

    MissionFileSubmitResponse submitMissionFile(
            Long userId,
            Long storyId,
            Long chapterId,
            MissionFileSubmitRequest request
    );
}
