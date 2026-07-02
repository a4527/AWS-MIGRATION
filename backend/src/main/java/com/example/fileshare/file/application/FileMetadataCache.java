package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;
import java.util.Optional;

public interface FileMetadataCache {

    Optional<FileMetadata> findById(Long id);

    void put(FileMetadata fileMetadata);

    void evict(Long id);
}
