package com.example.fileshare.file.repository.jpa;

import org.springframework.data.domain.Limit;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import com.example.fileshare.file.domain.ScanStatus;

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
    public Optional<FileMetadata> findByStoragePath(String storagePath) {
        return jpaRepository.findFirstByStoragePath(storagePath).map(FileMetadataEntity::toDomain);
    }

    @Override
    public List<FileMetadata> findVisibleByOwnerId(Long ownerId, Long beforeId, int limit) {
        List<FileMetadataEntity> rows = beforeId == null
                ? jpaRepository.findByOwnerIdAndStatusNotOrderByIdDesc(ownerId, FileStatus.DELETED, Limit.of(limit))
                : jpaRepository.findByOwnerIdAndStatusNotAndIdLessThanOrderByIdDesc(
                        ownerId, FileStatus.DELETED, beforeId, Limit.of(limit));
        return rows.stream()
                .map(FileMetadataEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int applyScanResult(String storagePath, ScanStatus scanStatus, FileStatus status, Instant at) {
        return jpaRepository.applyScanResult(storagePath, scanStatus, status, at);
    }

    @Override
    public List<String> findPendingScanBefore(Instant before) {
        return jpaRepository.findPendingScanBefore(before);
    }
}
