package com.atoria.backend.domain.story.repository;

import com.atoria.backend.domain.story.entity.Story;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryRepository extends JpaRepository<Story, Long> {

    @Query(
            value = """
                    select
                        s.story_id as storyId,
                        s.course_id as courseId,
                        s.title as title,
                        s.status as status,
                        max(gf.thumbnail_key) as thumbnailUrl,
                        coalesce(count(distinct case when ucp.is_completed = true then c.chapter_id end), 0) as completedCount,
                        coalesce(count(distinct c.chapter_id), 0) as totalCount,
                        s.created_at as createdAt
                    from stories s
                    left join chapters c on c.story_id = s.story_id
                    left join user_chapter_progress ucp
                        on ucp.chapter_id = c.chapter_id
                        and ucp.user_id = s.user_id
                    left join generated_files gf
                        on gf.story_id = s.story_id
                        and gf.user_id = s.user_id
                    where s.user_id = :userId
                    group by s.story_id, s.course_id, s.title, s.status, s.created_at
                    order by s.created_at desc
                    """,
            countQuery = """
                    select count(s.story_id)
                    from stories s
                    where s.user_id = :userId
                    """,
            nativeQuery = true
    )
    Page<StoryListRow> findMyStoryRows(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select s
            from Story s
            join fetch s.course
            where s.id = :storyId
              and s.user.id = :userId
            """)
    Optional<Story> findByIdAndUserIdWithCourse(@Param("storyId") Long storyId, @Param("userId") Long userId);

    @Query(
            value = """
                    select
                        c.chapter_id as chapterId,
                        c.sequence as sequence,
                        p.place_id as placeId,
                        p.name as placeTitle,
                        c.story_content ->> 'content' as storyContent,
                        coalesce(ucp.is_completed, false) as isCompleted
                    from chapters c
                    join course_places cp on cp.course_place_id = c.course_place_id
                    join places p on p.place_id = cp.place_id
                    left join user_chapter_progress ucp
                        on ucp.chapter_id = c.chapter_id
                        and ucp.user_id = :userId
                    where c.story_id = :storyId
                    order by c.sequence asc
                    """,
            nativeQuery = true
    )
    List<StoryChapterRow> findChapterRows(@Param("storyId") Long storyId, @Param("userId") Long userId);

    @Query(
            value = """
                    select
                        s.story_id as storyId,
                        coalesce(count(distinct c.chapter_id), 0) as totalCount,
                        coalesce(count(distinct case when ucp.is_completed = true then c.chapter_id end), 0) as completedCount
                    from stories s
                    left join chapters c on c.story_id = s.story_id
                    left join user_chapter_progress ucp
                        on ucp.chapter_id = c.chapter_id
                        and ucp.user_id = s.user_id
                    where s.story_id = :storyId
                      and s.user_id = :userId
                    group by s.story_id
                    """,
            nativeQuery = true
    )
    Optional<StoryProgressRow> findProgressRow(@Param("storyId") Long storyId, @Param("userId") Long userId);

    @Query(
            value = """
                    select
                        c.chapter_id as chapterId,
                        p.name as placeTitle,
                        coalesce(ucp.is_completed, false) as isCompleted,
                        ucp.location_verification_status as locationVerificationStatus
                    from chapters c
                    join stories s on s.story_id = c.story_id
                    join course_places cp on cp.course_place_id = c.course_place_id
                    join places p on p.place_id = cp.place_id
                    left join user_chapter_progress ucp
                        on ucp.chapter_id = c.chapter_id
                        and ucp.user_id = s.user_id
                    where s.story_id = :storyId
                      and s.user_id = :userId
                    order by c.sequence asc
                    """,
            nativeQuery = true
    )
    List<StoryProgressChapterRow> findProgressChapterRows(@Param("storyId") Long storyId, @Param("userId") Long userId);
}
