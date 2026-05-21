package com.atoria.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int code,
        String message,
        T data,
        PageInfo pageInfo,
        String error
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, HttpStatus.OK.value(), message, data, null, null);
    }

    public static <T> ApiResponse<T> success(String message, T data, PageInfo pageInfo) {
        return new ApiResponse<>(true, HttpStatus.OK.value(), message, data, pageInfo, null);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(true, HttpStatus.CREATED.value(), message, data, null, null);
    }

    public static <T> ApiResponse<T> accepted(String message, T data) {
        return new ApiResponse<>(true, HttpStatus.ACCEPTED.value(), message, data, null, null);
    }

    public static ApiResponse<Void> fail(HttpStatus status, String message, String error) {
        return new ApiResponse<>(false, status.value(), message, null, null, error);
    }
}
