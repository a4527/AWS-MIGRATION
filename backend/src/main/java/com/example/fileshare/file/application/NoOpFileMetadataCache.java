package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
public class NoOpFileMetadataCache implements FileMetadataCache {

    @Override
    public Optional<FileMetadata> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public void put(FileMetadata fileMetadata) {
    }

    @Override
    public void evict(Long id) {
    }
}
