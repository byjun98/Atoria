package com.atoria.backend.domain.course.service;

import com.atoria.backend.domain.course.dto.response.PlaceDetailResponse;
import com.atoria.backend.domain.course.dto.response.PlaceListResponse;
import org.springframework.data.domain.Page;

public interface PlaceService {

    Page<PlaceListResponse> getPlaces(String category, String keyword, int page, int size);

    PlaceDetailResponse getPlace(Long placeId);
}
