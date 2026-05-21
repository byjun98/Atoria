package com.atoria.backend.domain.file.controller;

import com.atoria.backend.domain.file.dto.request.FileCreateRequest;
import com.atoria.backend.domain.file.dto.request.PresignedUrlRequest;
import com.atoria.backend.domain.file.dto.response.FileCreateResponse;
import com.atoria.backend.domain.file.dto.response.FileDetailResponse;
import com.atoria.backend.domain.file.dto.response.FileListResponse;
import com.atoria.backend.domain.file.dto.response.PresignedUrlResponse;
import com.atoria.backend.domain.file.service.FileService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/files/presigned-url")
    public PresignedUrlResponse getPresignedUrl(@Valid @RequestBody PresignedUrlRequest request) {
        return fileService.getPresignedUrl(request);
    }

    @PostMapping("/ebooks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<FileCreateResponse> createEbook(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FileCreateRequest request
    ) {
        return ApiResponse.accepted("결과물 생성 요청 완료", fileService.createEbook(userId, request));
    }

    @GetMapping("/ebooks")
    public ApiResponse<List<FileListResponse>> getEbooks(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<FileListResponse> ebooks = fileService.getEbooks(userId, page, size);
        return ApiResponse.success("결과물 목록 조회 성공", ebooks.getContent(), PageInfo.from(ebooks));
    }

    @GetMapping("/ebooks/{ebookId}")
    public ApiResponse<FileDetailResponse> getEbook(
            @AuthenticationPrincipal Long userId,
            @PathVariable String ebookId
    ) {
        return ApiResponse.success("결과물 상세 조회 성공", fileService.getEbook(userId, ebookId));
    }
}
