package com.atoria.backend.domain.story.service;

import com.atoria.backend.domain.chapter.entity.Chapter;
import com.atoria.backend.domain.chapter.repository.ChapterRepository;
import com.atoria.backend.domain.course.entity.Course;
import com.atoria.backend.domain.course.entity.CoursePlace;
import com.atoria.backend.domain.course.entity.Place;
import com.atoria.backend.domain.course.repository.CoursePlaceRepository;
import com.atoria.backend.domain.course.repository.CourseRepository;
import com.atoria.backend.domain.course.repository.PlaceRepository;
import com.atoria.backend.domain.story.client.AiStoryClient;
import com.atoria.backend.domain.story.client.AiStoryIntroRequest;
import com.atoria.backend.domain.story.client.AiStoryIntroResponse;
import com.atoria.backend.domain.story.dto.request.StoryCreateRequest;
import com.atoria.backend.domain.story.dto.response.StoryCreateResponse;
import com.atoria.backend.domain.story.dto.response.StoryDetailResponse;
import com.atoria.backend.domain.story.dto.response.StoryListResponse;
import com.atoria.backend.domain.story.dto.response.StoryProgressResponse;
import com.atoria.backend.domain.story.entity.Story;
import com.atoria.backend.domain.story.repository.StoryChapterRow;
import com.atoria.backend.domain.story.repository.StoryProgressChapterRow;
import com.atoria.backend.domain.story.repository.StoryProgressRow;
import com.atoria.backend.domain.story.repository.StoryRepository;
import com.atoria.backend.domain.user.entity.User;
import com.atoria.backend.domain.user.repository.UserRepository;
import com.atoria.backend.global.exception.BusinessException;
import com.atoria.backend.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final CourseRepository courseRepository;
    private final CoursePlaceRepository coursePlaceRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AiStoryClient aiStoryClient;

    public StoryServiceImpl(
            StoryRepository storyRepository,
            ChapterRepository chapterRepository,
            CourseRepository courseRepository,
            CoursePlaceRepository coursePlaceRepository,
            PlaceRepository placeRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            AiStoryClient aiStoryClient
    ) {
        this.storyRepository = storyRepository;
        this.chapterRepository = chapterRepository;
        this.courseRepository = courseRepository;
        this.coursePlaceRepository = coursePlaceRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.aiStoryClient = aiStoryClient;
    }

    @Override
    @Transactional
    public StoryCreateResponse createStory(Long userId, StoryCreateRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Course course = courseRepository.findByIdWithPlaces(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        List<SelectedCoursePlace> selectedCoursePlaces = resolveCoursePlaces(course, request.placeIds());
        AiStoryIntroResponse aiStory = aiStoryClient.createStoryIntro(
                createAiStoryRequest(request, selectedCoursePlaces)
        );

        Story story = storyRepository.save(Story.create(
                user,
                course,
                createStoryTitle(request, course, selectedCoursePlaces),
                serializeProtagonists(request.protagonists()),
                aiStory.intro(),
                aiStory.outro()
        ));
        List<Chapter> chapters = chapterRepository.saveAll(createChapters(story, selectedCoursePlaces, aiStory));

        return StoryCreateResponse.of(story, chapters);
    }

    @Override
    public Page<StoryListResponse> getStories(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return storyRepository.findMyStoryRows(userId, pageable)
                .map(StoryListResponse::from);
    }

    @Override
    public StoryDetailResponse getStory(Long userId, Long storyId) {
        Story story = storyRepository.findByIdAndUserIdWithCourse(storyId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        List<StoryChapterRow> chapterRows = storyRepository.findChapterRows(storyId, userId);

        return StoryDetailResponse.of(story, chapterRows);
    }

    @Override
    public StoryProgressResponse getStoryProgress(Long userId, Long storyId) {
        StoryProgressRow progressRow = storyRepository.findProgressRow(storyId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
        List<StoryProgressChapterRow> chapterRows = storyRepository.findProgressChapterRows(storyId, userId);

        return StoryProgressResponse.of(progressRow, chapterRows);
    }

    private List<SelectedCoursePlace> resolveCoursePlaces(Course course, List<Long> requestedPlaceIds) {
        if (requestedPlaceIds == null || requestedPlaceIds.isEmpty()) {
            return course.getCoursePlaces().stream()
                    .map(coursePlace -> new SelectedCoursePlace(coursePlace, coursePlace.getSequence()))
                    .toList();
        }

        List<Long> selectedPlaceIds = requestedPlaceIds.stream()
                .filter(placeId -> placeId != null)
                .distinct()
                .toList();
        if (selectedPlaceIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }

        Map<Long, CoursePlace> existingCoursePlacesByPlaceId = new HashMap<>();
        course.getCoursePlaces().forEach(coursePlace ->
                existingCoursePlacesByPlaceId.putIfAbsent(coursePlace.getPlace().getId(), coursePlace)
        );
        Map<Long, Place> placesById = new HashMap<>();
        placeRepository.findAllById(selectedPlaceIds).forEach(place -> placesById.put(place.getId(), place));
        if (placesById.size() != selectedPlaceIds.size()) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }

        List<CoursePlace> coursePlacesToSave = new ArrayList<>();
        List<SelectedCoursePlace> selectedCoursePlaces = new ArrayList<>();
        for (int index = 0; index < selectedPlaceIds.size(); index++) {
            Long placeId = selectedPlaceIds.get(index);
            int sequence = index + 1;
            CoursePlace coursePlace = existingCoursePlacesByPlaceId.get(placeId);
            if (coursePlace == null) {
                coursePlace = CoursePlace.create(course, placesById.get(placeId), sequence);
                coursePlacesToSave.add(coursePlace);
            }
            selectedCoursePlaces.add(new SelectedCoursePlace(coursePlace, sequence));
        }
        if (!coursePlacesToSave.isEmpty()) {
            coursePlaceRepository.saveAll(coursePlacesToSave);
        }
        return selectedCoursePlaces;
    }

    private AiStoryIntroRequest createAiStoryRequest(
            StoryCreateRequest request,
            List<SelectedCoursePlace> selectedCoursePlaces
    ) {
        return new AiStoryIntroRequest(
                request.protagonists().size(),
                request.protagonists().stream()
                        .map(protagonist -> new AiStoryIntroRequest.PersonInfo(
                                protagonist.name(),
                                protagonist.age(),
                                protagonist.tendency()
                        ))
                        .toList(),
                selectedCoursePlaces.stream()
                        .map(this::toAiStoryPlace)
                        .toList()
        );
    }

    private AiStoryIntroRequest.StoryPlace toAiStoryPlace(SelectedCoursePlace selectedCoursePlace) {
        Place place = selectedCoursePlace.coursePlace().getPlace();
        return new AiStoryIntroRequest.StoryPlace(
                place.getId(),
                selectedCoursePlace.sequence(),
                place.getTitle(),
                valueOrEmpty(place.getDescription()),
                valueOrEmpty(place.getAddress()),
                valueOrEmpty(place.getCategory()),
                toDouble(place.getLatitude()),
                toDouble(place.getLongitude())
        );
    }

    private List<Chapter> createChapters(
            Story story,
            List<SelectedCoursePlace> selectedCoursePlaces,
            AiStoryIntroResponse aiStory
    ) {
        List<Chapter> chapters = new ArrayList<>();
        int chapterSequence = 1;

        for (SelectedCoursePlace selectedCoursePlace : selectedCoursePlaces) {
            List<AiStoryIntroResponse.Mission> missions = findMissions(
                    aiStory,
                    selectedCoursePlace.sequence()
            );
            for (AiStoryIntroResponse.Mission mission : missions) {
                chapters.add(Chapter.create(
                        story,
                        selectedCoursePlace.coursePlace(),
                        chapterSequence++,
                        mission.title(),
                        mission.description(),
                        mission.verificationHint(),
                        mission.type(),
                        serializeStoryContent(mission.story())
                ));
            }
        }

        return chapters;
    }

    private List<AiStoryIntroResponse.Mission> findMissions(AiStoryIntroResponse aiStory, int sequence) {
        if (aiStory.missions() == null) {
            throw new BusinessException(ErrorCode.AI_STORY_GENERATION_FAILED);
        }

        List<AiStoryIntroResponse.Mission> missions = aiStory.missions().stream()
                .filter(mission -> mission.sequence() == sequence)
                .toList();
        if (missions.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_STORY_GENERATION_FAILED);
        }
        return missions;
    }

    private String createStoryTitle(
            StoryCreateRequest request,
            Course course,
            List<SelectedCoursePlace> selectedCoursePlaces
    ) {
        String protagonistName = request.protagonists().getFirst().name();
        if (request.placeIds() == null || request.placeIds().isEmpty()) {
            return protagonistName + "이의 " + course.getTitle();
        }
        String selectedCourseTitle = selectedCoursePlaces.stream()
                .map(selectedCoursePlace -> selectedCoursePlace.coursePlace().getPlace().getTitle())
                .reduce((left, right) -> left + "-" + right)
                .orElse(course.getTitle());
        return protagonistName + "이의 " + selectedCourseTitle + " 코스";
    }

    private String serializeProtagonists(List<StoryCreateRequest.ProtagonistRequest> protagonists) {
        try {
            return objectMapper.writeValueAsString(protagonists);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("주인공 정보를 JSON으로 변환할 수 없습니다.", exception);
        }
    }

    private String serializeStoryContent(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of("content", valueOrEmpty(content)));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.AI_STORY_GENERATION_FAILED);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private record SelectedCoursePlace(
            CoursePlace coursePlace,
            int sequence
    ) {
    }
}
