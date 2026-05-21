package com.atoria.backend.global.response;

import org.springframework.data.domain.Page;

public record PageInfo(
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static PageInfo from(Page<?> page) {
        return new PageInfo(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
