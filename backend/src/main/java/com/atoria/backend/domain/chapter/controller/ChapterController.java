package com.atoria.backend.domain.chapter.controller;

import com.atoria.backend.domain.chapter.dto.request.MissionFileSubmitRequest;
import com.atoria.backend.domain.chapter.dto.request.MissionResultSubmitRequest;
import com.atoria.backend.domain.chapter.dto.response.ChapterDetailResponse;
import com.atoria.backend.domain.chapter.dto.response.MissionFileSubmitResponse;
import com.atoria.backend.domain.chapter.dto.response.MissionResultSubmitResponse;
import com.atoria.backend.domain.chapter.service.ChapterService;
import com.atoria.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stories/{storyId}/chapters")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @GetMapping("/{chapterId}")
    public ApiResponse<ChapterDetailResponse> getChapter(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long storyId,
            @PathVariable Long chapterId
    ) {
        return ApiResponse.success("\uCC55\uD130 \uC0C1\uC138 \uC870\uD68C \uC131\uACF5",
                chapterService.getChapter(userId, storyId, chapterId));
    }

    @PostMapping("/{chapterId}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionResultSubmitResponse> submitMissionResult(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long storyId,
            @PathVariable Long chapterId,
            @Valid @RequestBody MissionResultSubmitRequest request
    ) {
        return ApiResponse.created("\uBBF8\uC158 \uACB0\uACFC \uC81C\uCD9C \uC644\uB8CC",
                chapterService.submitMissionResult(userId, storyId, chapterId, request));
    }

    @PostMapping("/{chapterId}/files")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionFileSubmitResponse> submitMissionFile(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long storyId,
            @PathVariable Long chapterId,
            @Valid @RequestBody MissionFileSubmitRequest request
    ) {
        return ApiResponse.created("\uBBF8\uC158 \uD30C\uC77C \uC81C\uCD9C \uC644\uB8CC",
                chapterService.submitMissionFile(userId, storyId, chapterId, request));
    }
}
