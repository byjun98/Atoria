package com.atoria.backend.domain.chapter.repository;

import com.atoria.backend.domain.chapter.entity.UserChapterProgress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserChapterProgressRepository extends JpaRepository<UserChapterProgress, Long> {

    Optional<UserChapterProgress> findByUserIdAndChapterId(Long userId, Long chapterId);
}
