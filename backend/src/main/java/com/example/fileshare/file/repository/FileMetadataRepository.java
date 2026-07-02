package com.example.fileshare.file.repository;

import com.example.fileshare.file.domain.FileMetadata;
import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository {

    FileMetadata save(FileMetadata fileMetadata);

    Optional<FileMetadata> findById(Long id);

    List<FileMetadata> findVisibleByOwnerId(Long ownerId);
}
