package com.atoria.backend.domain.story.controller;

import com.atoria.backend.domain.story.dto.request.StoryCreateRequest;
import com.atoria.backend.domain.story.dto.response.StoryCreateResponse;
import com.atoria.backend.domain.story.dto.response.StoryDetailResponse;
import com.atoria.backend.domain.story.dto.response.StoryListResponse;
import com.atoria.backend.domain.story.dto.response.StoryProgressResponse;
import com.atoria.backend.domain.story.service.StoryService;
import com.atoria.backend.global.response.ApiResponse;
import com.atoria.backend.global.response.PageInfo;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stories")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoryCreateResponse> createStory(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody StoryCreateRequest request
    ) {
        return ApiResponse.created("스토리 생성 성공", storyService.createStory(userId, request));
    }

    @GetMapping
    public ApiResponse<List<StoryListResponse>> getStories(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<StoryListResponse> stories = storyService.getStories(userId, page, size);
        return ApiResponse.success("스토리 목록 조회 성공", stories.getContent(), PageInfo.from(stories));
    }

    @GetMapping("/{storyId}")
    public ApiResponse<StoryDetailResponse> getStory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long storyId
    ) {
        return ApiResponse.success("스토리 상세 조회 성공", storyService.getStory(userId, storyId));
    }

    @GetMapping("/{storyId}/progress")
    public ApiResponse<StoryProgressResponse> getStoryProgress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long storyId
    ) {
        return ApiResponse.success("스토리 진행률 조회 성공", storyService.getStoryProgress(userId, storyId));
    }
}
