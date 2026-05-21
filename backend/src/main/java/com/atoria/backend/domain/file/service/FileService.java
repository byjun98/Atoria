package com.atoria.backend.domain.file.service;

import com.atoria.backend.domain.file.dto.request.FileCreateRequest;
import com.atoria.backend.domain.file.dto.request.PresignedUrlRequest;
import com.atoria.backend.domain.file.dto.response.FileCreateResponse;
import com.atoria.backend.domain.file.dto.response.FileDetailResponse;
import com.atoria.backend.domain.file.dto.response.FileListResponse;
import com.atoria.backend.domain.file.dto.response.PresignedUrlResponse;
import org.springframework.data.domain.Page;

public interface FileService {

    PresignedUrlResponse getPresignedUrl(PresignedUrlRequest request);

    FileCreateResponse createEbook(Long userId, FileCreateRequest request);

    Page<FileListResponse> getEbooks(Long userId, int page, int size);

    FileDetailResponse getEbook(Long userId, String ebookId);
}
