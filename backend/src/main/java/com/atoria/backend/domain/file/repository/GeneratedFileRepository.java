package com.atoria.backend.domain.file.repository;

import com.atoria.backend.domain.file.entity.GeneratedFile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedFileRepository extends JpaRepository<GeneratedFile, String> {

    Page<GeneratedFile> findByUser_Id(Long userId, Pageable pageable);

    Optional<GeneratedFile> findByIdAndUser_Id(String id, Long userId);
}
