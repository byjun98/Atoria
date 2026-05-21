package com.atoria.backend.domain.course.dto.response;

import com.atoria.backend.domain.course.entity.Place;
import java.math.BigDecimal;

public record PlaceListResponse(
        Long placeId,
        String title,
        BigDecimal latitude,
        BigDecimal longitude,
        String thumbnailUrl
) {

    public static PlaceListResponse from(Place place) {
        return new PlaceListResponse(
                place.getId(),
                place.getTitle(),
                place.getLatitude(),
                place.getLongitude(),
                place.getThumbnailUrl()
        );
    }
}
