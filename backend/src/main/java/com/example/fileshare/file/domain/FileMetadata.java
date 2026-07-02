package com.example.fileshare.file.domain;

import java.time.Instant;

public class FileMetadata {

    private final Long id;
    private final Long ownerId;
    private final String originalFileName;
    private final String storedFileName;
    private final String storagePath;
    private final long size;
    private final String mimeType;
    private FileStatus status;
    private ScanStatus scanStatus;
    private final String description;
    private Instant deletedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public FileMetadata(
            Long id,
            Long ownerId,
            String originalFileName,
            String storedFileName,
            String storagePath,
            long size,
            String mimeType,
            FileStatus status,
            ScanStatus scanStatus,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.storagePath = storagePath;
        this.size = size;
        this.mimeType = mimeType;
        this.status = status;
        this.scanStatus = scanStatus;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long id() {
        return id;
    }

    public Long ownerId() {
        return ownerId;
    }

    public String originalFileName() {
        return originalFileName;
    }

    public String storedFileName() {
        return storedFileName;
    }

    public String storagePath() {
        return storagePath;
    }

    public long size() {
        return size;
    }

    public String mimeType() {
        return mimeType;
    }

    public FileStatus status() {
        return status;
    }

    public ScanStatus scanStatus() {
        return scanStatus;
    }

    public String description() {
        return description;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void markDeleted() {
        this.status = FileStatus.DELETED;
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public FileMetadata withId(Long id) {
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
        metadata.deletedAt = deletedAt;
        return metadata;
    }

    public FileMetadata withDeletedAt(Instant deletedAt) {
        this.status = FileStatus.DELETED;
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
        return this;
    }
}
