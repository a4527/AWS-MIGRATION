package com.example.fileshare.file.repository.jpa;

import com.example.fileshare.file.domain.FileStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataFileMetadataJpaRepository extends JpaRepository<FileMetadataEntity, Long> {

    List<FileMetadataEntity> findByOwnerIdAndStatusNotOrderByCreatedAtDesc(Long ownerId, FileStatus status);

    Optional<FileMetadataEntity> findFirstByStoragePath(String storagePath);
}
