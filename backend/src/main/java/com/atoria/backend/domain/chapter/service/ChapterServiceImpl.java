package com.atoria.backend.domain.chapter.service;

import com.atoria.backend.domain.chapter.dto.request.MissionFileSubmitRequest;
import com.atoria.backend.domain.chapter.dto.request.MissionResultSubmitRequest;
import com.atoria.backend.domain.chapter.dto.response.ChapterDetailResponse;
import com.atoria.backend.domain.chapter.dto.response.MissionFileSubmitResponse;
import com.atoria.backend.domain.chapter.dto.response.MissionResultSubmitResponse;
import com.atoria.backend.domain.chapter.entity.Chapter;
import com.atoria.backend.domain.chapter.entity.UserChapterProgress;
import com.atoria.backend.domain.chapter.repository.ChapterDetailRow;
import com.atoria.backend.domain.chapter.repository.ChapterRepository;
import com.atoria.backend.domain.chapter.repository.UserChapterProgressRepository;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChapterServiceImpl implements ChapterService {

    private final ChapterRepository chapterRepository;
    private final UserChapterProgressRepository userChapterProgressRepository;

    public ChapterServiceImpl(
            ChapterRepository chapterRepository,
            UserChapterProgressRepository userChapterProgressRepository
    ) {
        this.chapterRepository = chapterRepository;
        this.userChapterProgressRepository = userChapterProgressRepository;
    }

    @Override
    public ChapterDetailResponse getChapter(Long userId, Long storyId, Long chapterId) {
        ChapterDetailRow row = chapterRepository.findDetailRow(userId, storyId, chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAPTER_NOT_FOUND));

        return ChapterDetailResponse.from(row);
    }

    @Override
    @Transactional
    public MissionResultSubmitResponse submitMissionResult(
            Long userId,
            Long storyId,
            Long chapterId,
            MissionResultSubmitRequest request
    ) {
        Chapter chapter = chapterRepository.findByStoryIdAndUserIdAndChapterId(storyId, userId, chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAPTER_NOT_FOUND));
        UserChapterProgress progress = userChapterProgressRepository.findByUserIdAndChapterId(userId, chapterId)
                .orElseGet(() -> UserChapterProgress.create(chapter.getStory().getUser(), chapter));

        progress.submitResult(request.result().isCompleted());
        userChapterProgressRepository.save(progress);
        chapter.getStory().updateProgressStatus(chapterRepository.areAllChaptersCompleted(storyId, userId));

        Long nextChapterId = chapterRepository.findNextChapterId(storyId, chapter.getSequence())
                .orElse(null);

        return new MissionResultSubmitResponse(
                storyId,
                chapterId,
                progress.isCompleted(),
                progress.getCompletedAt(),
                nextChapterId
        );
    }

    @Override
    @Transactional
    public MissionFileSubmitResponse submitMissionFile(
            Long userId,
            Long storyId,
            Long chapterId,
            MissionFileSubmitRequest request
    ) {
        Chapter chapter = chapterRepository.findByStoryIdAndUserIdAndChapterId(storyId, userId, chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAPTER_NOT_FOUND));
        UserChapterProgress progress = userChapterProgressRepository.findByUserIdAndChapterId(userId, chapterId)
                .orElseGet(() -> UserChapterProgress.create(chapter.getStory().getUser(), chapter));

        String locationVerificationStatus = normalizeLocationVerificationStatus(request.locationVerificationStatus());
        var uploadedAt = progress.submitFile(request.fileUrl(), locationVerificationStatus);
        userChapterProgressRepository.save(progress);

        return new MissionFileSubmitResponse(
                storyId,
                chapterId,
                request.fileUrl(),
                request.type(),
                locationVerificationStatus,
                uploadedAt
        );
    }

    private String normalizeLocationVerificationStatus(String status) {
        if (status == null || status.isBlank()) {
            return UserChapterProgress.LOCATION_VERIFICATION_UNVERIFIED;
        }
        return status;
    }
}
