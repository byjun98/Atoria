package com.atoria.backend.domain.course.dto.response;

import com.atoria.backend.domain.course.entity.Place;

import java.math.BigDecimal;

public record PlaceDetailResponse (
        Long placeId,
        String title,
        BigDecimal latitude,
        BigDecimal longitude,
        String description,
        String address,
        String category,
        String thumbnailUrl
) {
    public static PlaceDetailResponse from(Place place) {
        return new PlaceDetailResponse(
                place.getId(),
                place.getTitle(),
                place.getLatitude(),
                place.getLongitude(),
                place.getDescription(),
                place.getAddress(),
                place.getCategory(),
                place.getThumbnailUrl()
        );
    }
}
