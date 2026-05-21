package com.atoria.backend.domain.story.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import com.atoria.backend.domain.story.entity.Story;
import com.atoria.backend.domain.story.repository.StoryRepository;
import com.atoria.backend.domain.user.entity.User;
import com.atoria.backend.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoryServiceImplTest {

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CoursePlaceRepository coursePlaceRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiStoryClient aiStoryClient;

    private StoryServiceImpl storyService;

    @BeforeEach
    void setUp() {
        storyService = new StoryServiceImpl(
                storyRepository,
                chapterRepository,
                courseRepository,
                coursePlaceRepository,
                placeRepository,
                userRepository,
                new ObjectMapper(),
                aiStoryClient
        );
    }

    @Test
    void createStorySavesMultipleMissionsForOnePlaceAsSeparateChapters() throws Exception {
        User user = User.create("user@example.com", "password", "tester");
        ReflectionTestUtils.setField(user, "id", 1L);

        Course course = newEntity(Course.class);
        ReflectionTestUtils.setField(course, "id", 10L);
        ReflectionTestUtils.setField(course, "title", "경주 코스");

        CoursePlace firstPlace = coursePlace(course, place(101L, "첨성대"), 1L, 1);
        CoursePlace secondPlace = coursePlace(course, place(102L, "불국사"), 2L, 2);
        ReflectionTestUtils.setField(course, "coursePlaces", new ArrayList<>(List.of(firstPlace, secondPlace)));

        AiStoryIntroResponse aiStory = new AiStoryIntroResponse(
                "intro",
                List.of(
                        mission(1, "첨성대 첫 번째 미션"),
                        mission(1, "첨성대 두 번째 미션"),
                        mission(2, "불국사 미션")
                ),
                "outro"
        );

        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(courseRepository.findByIdWithPlaces(10L)).thenReturn(Optional.of(course));
        when(aiStoryClient.createStoryIntro(any(AiStoryIntroRequest.class))).thenReturn(aiStory);
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StoryCreateRequest request = new StoryCreateRequest(
                10L,
                List.of(new StoryCreateRequest.ProtagonistRequest("민지", 8, "curious")),
                List.of()
        );

        StoryCreateResponse response = storyService.createStory(1L, request);

        assertThat(response.chapters()).hasSize(3);
        assertThat(response.chapters())
                .extracting("sequence")
                .containsExactly(1, 2, 3);
        assertThat(response.chapters())
                .extracting("placeTitle")
                .containsExactly("첨성대", "첨성대", "불국사");
    }

    private static CoursePlace coursePlace(Course course, Place place, Long id, int sequence) {
        CoursePlace coursePlace = CoursePlace.create(course, place, sequence);
        ReflectionTestUtils.setField(coursePlace, "id", id);
        return coursePlace;
    }

    private static Place place(Long id, String title) throws Exception {
        Place place = newEntity(Place.class);
        ReflectionTestUtils.setField(place, "id", id);
        ReflectionTestUtils.setField(place, "title", title);
        ReflectionTestUtils.setField(place, "latitude", BigDecimal.ZERO);
        ReflectionTestUtils.setField(place, "longitude", BigDecimal.ZERO);
        return place;
    }

    private static AiStoryIntroResponse.Mission mission(int sequence, String title) {
        return new AiStoryIntroResponse.Mission(
                sequence,
                title,
                title + " 설명",
                title + " 힌트",
                "PHOTO",
                title + " 이야기"
        );
    }

    private static <T> T newEntity(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
