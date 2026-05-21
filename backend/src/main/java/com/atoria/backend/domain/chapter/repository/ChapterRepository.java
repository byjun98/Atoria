package com.atoria.backend.domain.chapter.repository;

import com.atoria.backend.domain.chapter.entity.Chapter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    @Query(
            value = """
                    select
                        c.chapter_id as chapterId,
                        c.sequence as sequence,
                        coalesce(ucp.is_completed, false) as isCompleted,
                        p.place_id as placeId,
                        p.name as placeTitle,
                        p.latitude as latitude,
                        p.longitude as longitude,
                        c.story_content ->> 'content' as storyContent,
                        c.mission_title as missionTitle,
                        c.mission_description as missionDescription,
                        c.mission_verification_hint as missionVerificationHint,
                        c.mission_type as missionType,
                        ucp.file_url as fileUrl,
                        ucp.location_verification_status as locationVerificationStatus,
                        ucp.completed_at as completedAt
                    from chapters c
                    join stories s on s.story_id = c.story_id
                    join course_places cp on cp.course_place_id = c.course_place_id
                    join places p on p.place_id = cp.place_id
                    left join user_chapter_progress ucp
                        on ucp.chapter_id = c.chapter_id
                        and ucp.user_id = s.user_id
                    where s.story_id = :storyId
                      and s.user_id = :userId
                      and c.chapter_id = :chapterId
                    """,
            nativeQuery = true
    )
    Optional<ChapterDetailRow> findDetailRow(
            @Param("userId") Long userId,
            @Param("storyId") Long storyId,
            @Param("chapterId") Long chapterId
    );

    @Query(
            value = """
                    select
                        c.sequence as sequence,
                        p.place_id as placeId,
                        p.name as placeName,
                        p.address as placeAddress,
                        c.mission_title as missionTitle,
                        c.mission_description as missionDescription,
                        c.mission_type as missionType,
                        c.story_content ->> 'content' as storyContent,
                        ucp.file_url as fileUrl,
                        coalesce(ucp.choice, ucp.input_text) as choice
                    from chapters c
                    join stories s on s.story_id = c.story_id
                    join course_places cp on cp.course_place_id = c.course_place_id
                    join places p on p.place_id = cp.place_id
                    left join user_chapter_progress ucp
                        on ucp.chapter_id = c.chapter_id
                        and ucp.user_id = :userId
                    where s.story_id = :storyId
                      and s.user_id = :userId
                    order by c.sequence asc
                    """,
            nativeQuery = true
    )
    List<EbookChapterRow> findEbookChapterRows(
            @Param("userId") Long userId,
            @Param("storyId") Long storyId
    );

    @Query("""
            select c
            from Chapter c
            join fetch c.story s
            join fetch s.user
            where s.id = :storyId
              and s.user.id = :userId
              and c.id = :chapterId
            """)
    Optional<Chapter> findByStoryIdAndUserIdAndChapterId(
            @Param("storyId") Long storyId,
            @Param("userId") Long userId,
            @Param("chapterId") Long chapterId
    );

    @Query(
            value = """
                    select c.chapter_id
                    from chapters c
                    where c.story_id = :storyId
                      and c.sequence > :sequence
                    order by c.sequence asc
                    limit 1
                    """,
            nativeQuery = true
    )
    Optional<Long> findNextChapterId(@Param("storyId") Long storyId, @Param("sequence") int sequence);

    @Query(
            value = """
                    select count(c.chapter_id) = count(
                        case when ucp.is_completed = true then c.chapter_id end
                    )
                    from chapters c
                    join stories s on s.story_id = c.story_id
                    left join user_chapter_progress ucp
                        on ucp.chapter_id = c.chapter_id
                        and ucp.user_id = :userId
                    where s.story_id = :storyId
                      and s.user_id = :userId
                    """,
            nativeQuery = true
    )
    boolean areAllChaptersCompleted(@Param("storyId") Long storyId, @Param("userId") Long userId);
}
