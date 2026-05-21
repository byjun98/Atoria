package com.atoria.backend.domain.story.service;

import com.atoria.backend.domain.story.dto.request.StoryCreateRequest;
import com.atoria.backend.domain.story.dto.response.StoryCreateResponse;
import com.atoria.backend.domain.story.dto.response.StoryDetailResponse;
import com.atoria.backend.domain.story.dto.response.StoryListResponse;
import com.atoria.backend.domain.story.dto.response.StoryProgressResponse;
import org.springframework.data.domain.Page;

public interface StoryService {

    StoryCreateResponse createStory(Long userId, StoryCreateRequest request);

    Page<StoryListResponse> getStories(Long userId, int page, int size);

    StoryDetailResponse getStory(Long userId, Long storyId);

    StoryProgressResponse getStoryProgress(Long userId, Long storyId);
}
