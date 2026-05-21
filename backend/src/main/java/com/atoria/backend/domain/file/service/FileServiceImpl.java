package com.atoria.backend.domain.file.service;

import com.atoria.backend.domain.story.entity.Story;
import com.atoria.backend.domain.story.repository.StoryRepository;
import com.atoria.backend.domain.file.dto.request.FileCreateRequest;
import com.atoria.backend.domain.file.dto.request.PresignedUrlRequest;
import com.atoria.backend.domain.file.dto.response.FileCreateResponse;
import com.atoria.backend.domain.file.dto.response.FileDetailResponse;
import com.atoria.backend.domain.file.dto.response.FileListResponse;
import com.atoria.backend.domain.file.dto.response.PresignedUrlResponse;
import com.atoria.backend.domain.file.entity.GeneratedFile;
import com.atoria.backend.domain.file.repository.GeneratedFileRepository;
import com.atoria.backend.global.config.S3Properties;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Service
public class FileServiceImpl implements FileService {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final StoryRepository storyRepository;
    private final GeneratedFileRepository generatedFileRepository;
    private final ObjectMapper objectMapper;
    private final EbookGenerationAsyncService ebookGenerationAsyncService;

    public FileServiceImpl(
            S3Presigner s3Presigner,
            S3Properties s3Properties,
            StoryRepository storyRepository,
            GeneratedFileRepository generatedFileRepository,
            ObjectMapper objectMapper,
            EbookGenerationAsyncService ebookGenerationAsyncService
    ) {
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
        this.storyRepository = storyRepository;
        this.generatedFileRepository = generatedFileRepository;
        this.objectMapper = objectMapper;
        this.ebookGenerationAsyncService = ebookGenerationAsyncService;
    }

    @Override
    @Transactional(readOnly = true)
    public PresignedUrlResponse getPresignedUrl(PresignedUrlRequest request) {
        String objectKey = createObjectKey(request.fileName());
        PresignedPutObjectRequest presignedRequest = createPresignedRequest(request, objectKey);

        return new PresignedUrlResponse(
                presignedRequest.url().toString(),
                objectKey,
                createPublicObjectUrl(objectKey)
        );
    }

    @Override
    @Transactional
    public FileCreateResponse createEbook(Long userId, FileCreateRequest request) {
        Story story = storyRepository.findByIdAndUserIdWithCourse(request.storyId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        if (!story.getCourse().getId().equals(request.courseId())) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        GeneratedFile generatedFile = GeneratedFile.create(
                story.getUser(),
                story.getCourse(),
                story,
                request.title(),
                toJson(request.options())
        );
        generatedFileRepository.save(generatedFile);
        runAfterCommit(() -> ebookGenerationAsyncService.generate(userId, generatedFile.getId()));

        return new FileCreateResponse(generatedFile.getId(), generatedFile.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FileListResponse> getEbooks(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return generatedFileRepository.findByUser_Id(userId, pageable)
                .map(FileListResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDetailResponse getEbook(Long userId, String ebookId) {
        GeneratedFile generatedFile = generatedFileRepository.findByIdAndUser_Id(ebookId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "결과물을 찾을 수 없습니다."));

        return FileDetailResponse.of(generatedFile, toObject(generatedFile.getMetadata()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_FILE_OPTIONS);
        }
    }

    private Object toObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "결과물 메타데이터를 읽을 수 없습니다.");
        }
    }

    private PresignedPutObjectRequest createPresignedRequest(PresignedUrlRequest request, String objectKey) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(objectKey)
                    .contentType(request.contentType())
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(s3Properties.presignedUrlExpirationMinutes()))
                    .putObjectRequest(putObjectRequest)
                    .build();
            return s3Presigner.presignPutObject(presignRequest);
        } catch (SdkException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.S3_PRESIGNED_URL_FAILED);
        }
    }

    private String createObjectKey(String fileName) {
        String sanitizedFileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        String prefix = s3Properties.uploadPrefix();
        if (prefix == null || prefix.isBlank()) {
            return UUID.randomUUID() + "-" + sanitizedFileName;
        }
        return prefix.replaceAll("/+$", "") + "/" + UUID.randomUUID() + "-" + sanitizedFileName;
    }

    private String createPublicObjectUrl(String objectKey) {
        String normalizedObjectKey = objectKey.replaceAll("^/+", "");
        return "https://" + s3Properties.bucket() + ".s3." + s3Properties.region() + ".amazonaws.com/"
                + normalizedObjectKey;
    }

    private void runAfterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
