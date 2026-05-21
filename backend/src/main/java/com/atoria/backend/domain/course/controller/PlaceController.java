package com.atoria.backend.domain.course.controller;

import com.atoria.backend.domain.course.dto.response.PlaceDetailResponse;
import com.atoria.backend.domain.course.dto.response.PlaceListResponse;
import com.atoria.backend.domain.course.service.PlaceService;
import com.atoria.backend.global.response.ApiResponse;
import com.atoria.backend.global.response.PageInfo;
import org.springframework.data.domain.Page;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public ApiResponse<List<PlaceListResponse>> getPlaces(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PlaceListResponse> places = placeService.getPlaces(category, keyword, page, size);
        return ApiResponse.success("장소 목록 조회 성공", places.getContent(), PageInfo.from(places));
    }

    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponse> getPlace(@PathVariable Long placeId) {
        return ApiResponse.success("장소 상세 조회 성공", placeService.getPlace(placeId));
    }
}
