package com.example.fileshare.file.repository;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryFileMetadataRepository implements FileMetadataRepository {

    private final Map<Long, FileMetadata> filesById = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(100);

    @Override
    public FileMetadata save(FileMetadata fileMetadata) {
        FileMetadata saved = fileMetadata.id() == null
                ? fileMetadata.withId(sequence.getAndIncrement())
                : fileMetadata;
        filesById.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Optional<FileMetadata> findById(Long id) {
        return Optional.ofNullable(filesById.get(id));
    }

    @Override
    public List<FileMetadata> findVisibleByOwnerId(Long ownerId) {
        return filesById.values().stream()
                .filter(file -> file.ownerId().equals(ownerId))
                .filter(file -> file.status() != FileStatus.DELETED)
                .sorted(Comparator.comparing(FileMetadata::createdAt).reversed())
                .toList();
    }
}
