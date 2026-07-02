package com.example.fileshare.file.repository.jpa;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.repository.FileMetadataRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JpaFileMetadataRepositoryAdapter implements FileMetadataRepository {

    private final SpringDataFileMetadataJpaRepository jpaRepository;

    public JpaFileMetadataRepositoryAdapter(SpringDataFileMetadataJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public FileMetadata save(FileMetadata fileMetadata) {
        return jpaRepository.save(FileMetadataEntity.from(fileMetadata)).toDomain();
    }

    @Override
    public Optional<FileMetadata> findById(Long id) {
        return jpaRepository.findById(id).map(FileMetadataEntity::toDomain);
    }

    @Override
    public List<FileMetadata> findVisibleByOwnerId(Long ownerId) {
        return jpaRepository.findByOwnerIdAndStatusNotOrderByCreatedAtDesc(ownerId, FileStatus.DELETED)
                .stream()
                .map(FileMetadataEntity::toDomain)
                .toList();
    }
}
