package com.example.fileshare.file.repository.jpa;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "files")
public class FileMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, length = 255)
    private String storedFileName;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false)
    private long size;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FileStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 30)
    private ScanStatus scanStatus;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FileMetadataEntity() {
    }

    public static FileMetadataEntity from(FileMetadata metadata) {
        FileMetadataEntity entity = new FileMetadataEntity();
        entity.id = metadata.id();
        entity.ownerId = metadata.ownerId();
        entity.originalFileName = metadata.originalFileName();
        entity.storedFileName = metadata.storedFileName();
        entity.storagePath = metadata.storagePath();
        entity.size = metadata.size();
        entity.mimeType = metadata.mimeType();
        entity.status = metadata.status();
        entity.scanStatus = metadata.scanStatus();
        entity.description = metadata.description();
        entity.deletedAt = metadata.deletedAt();
        entity.createdAt = metadata.createdAt();
        entity.updatedAt = metadata.updatedAt();
        return entity;
    }

    public FileMetadata toDomain() {
        FileMetadata metadata = new FileMetadata(
                id,
                ownerId,
                originalFileName,
                storedFileName,
                storagePath,
                size,
                mimeType,
                status,
                scanStatus,
                description,
                createdAt,
                updatedAt
        );
        if (deletedAt != null) {
            metadata.withDeletedAt(deletedAt);
        }
        return metadata;
    }
}
