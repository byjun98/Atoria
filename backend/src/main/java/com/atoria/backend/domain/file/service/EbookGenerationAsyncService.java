package com.atoria.backend.domain.file.service;

import com.atoria.backend.domain.chapter.repository.ChapterRepository;
import com.atoria.backend.domain.chapter.repository.EbookChapterRow;
import com.atoria.backend.domain.file.client.AiEbookClient;
import com.atoria.backend.domain.file.client.AiEbookJobRequest;
import com.atoria.backend.domain.file.client.AiEbookJobResponse;
import com.atoria.backend.domain.file.entity.GeneratedFile;
import com.atoria.backend.domain.file.repository.GeneratedFileRepository;
import com.atoria.backend.domain.story.entity.Story;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import com.atoria.backend.global.config.S3Properties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EbookGenerationAsyncService {

    private static final String DEFAULT_AI_ERROR_MESSAGE = "AI E-book 생성에 실패했습니다.";

    private final GeneratedFileRepository generatedFileRepository;
    private final ChapterRepository chapterRepository;
    private final ObjectMapper objectMapper;
    private final AiEbookClient aiEbookClient;
    private final S3Properties s3Properties;

    public EbookGenerationAsyncService(
            GeneratedFileRepository generatedFileRepository,
            ChapterRepository chapterRepository,
            ObjectMapper objectMapper,
            AiEbookClient aiEbookClient,
            S3Properties s3Properties
    ) {
        this.generatedFileRepository = generatedFileRepository;
        this.chapterRepository = chapterRepository;
        this.objectMapper = objectMapper;
        this.aiEbookClient = aiEbookClient;
        this.s3Properties = s3Properties;
    }

    @Async("ebookGenerationTaskExecutor")
    @Transactional
    public void generate(Long userId, String ebookId) {
        GeneratedFile generatedFile = generatedFileRepository.findByIdAndUser_Id(ebookId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));

        try {
            Story story = generatedFile.getStory();
            List<EbookChapterRow> chapterRows = chapterRepository.findEbookChapterRows(userId, story.getId());
            AiEbookJobResponse aiResponse = aiEbookClient.createEbook(createAiEbookRequest(userId, story, chapterRows));

            generatedFile.complete(toJson(aiResponse.data().ebookContent()));
        } catch (RuntimeException exception) {
            generatedFile.fail(toJson(Map.of("error", DEFAULT_AI_ERROR_MESSAGE)));
        }
    }

    private AiEbookJobRequest createAiEbookRequest(Long userId, Story story, List<EbookChapterRow> chapterRows) {
        List<AiEbookJobRequest.PersonInfo> peopleInformation = parsePeopleInformation(story);

        return new AiEbookJobRequest(
                story.getId(),
                userId,
                new AiEbookJobRequest.StoryBlock(
                        story.getTitle(),
                        valueOrEmpty(story.getIntro()),
                        valueOrEmpty(story.getOutro()),
                        new AiEbookJobRequest.ProtagonistInfo(
                                peopleInformation.size(),
                                peopleInformation
                        )
                ),
                chapterRows.stream()
                        .map(this::toAiEbookChapter)
                        .toList()
        );
    }

    private List<AiEbookJobRequest.PersonInfo> parsePeopleInformation(Story story) {
        if (story.getProtagonistInfo() == null || story.getProtagonistInfo().isBlank()) {
            return List.of(new AiEbookJobRequest.PersonInfo(story.getUser().getNickname(), 0, ""));
        }

        try {
            return objectMapper.readValue(story.getProtagonistInfo(), new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.AI_EBOOK_GENERATION_FAILED);
        }
    }

    private AiEbookJobRequest.Chapter toAiEbookChapter(EbookChapterRow row) {
        return new AiEbookJobRequest.Chapter(
                row.getSequence(),
                row.getPlaceId(),
                valueOrEmpty(row.getPlaceName()),
                valueOrEmpty(row.getPlaceAddress()),
                valueOrEmpty(row.getMissionTitle()),
                valueOrEmpty(row.getMissionDescription()),
                normalizeMissionType(row.getMissionType()),
                valueOrEmpty(row.getStoryContent()),
                new AiEbookJobRequest.UserResult(
                        toPublicFileUrl(row.getFileUrl()),
                        blankToNull(row.getChoice())
                )
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_FILE_OPTIONS);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String toPublicFileUrl(String value) {
        String fileUrl = blankToNull(value);
        if (fileUrl == null || isHttpUrl(fileUrl)) {
            return fileUrl;
        }
        return createPublicObjectUrl(fileUrl);
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String createPublicObjectUrl(String objectKey) {
        String normalizedObjectKey = objectKey.replaceAll("^/+", "");
        return "https://" + s3Properties.bucket() + ".s3." + s3Properties.region() + ".amazonaws.com/"
                + normalizedObjectKey;
    }

    private String normalizeMissionType(String missionType) {
        if (missionType == null || missionType.isBlank()) {
            return "ACTION";
        }

        return switch (missionType) {
            case "IMAGE" -> "PHOTO";
            case "TEXT" -> "ACTION";
            default -> missionType;
        };
    }
}
