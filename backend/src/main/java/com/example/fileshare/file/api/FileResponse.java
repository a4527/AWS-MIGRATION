package com.example.fileshare.file.api;

import com.example.fileshare.file.domain.FileMetadata;
import java.time.Instant;

public record FileResponse(
        Long fileId,
        String originalFileName,
        String storedFileName,
        long size,
        String mimeType,
        String status,
        String scanStatus,
        Long ownerId,
        String description,
        Instant createdAt,
        Instant updatedAt
) {

    public static FileResponse from(FileMetadata fileMetadata) {
        return new FileResponse(
                fileMetadata.id(),
                fileMetadata.originalFileName(),
                fileMetadata.storedFileName(),
                fileMetadata.size(),
                fileMetadata.mimeType(),
                fileMetadata.status().name().toLowerCase(),
                fileMetadata.scanStatus().name(),
                fileMetadata.ownerId(),
                fileMetadata.description(),
                fileMetadata.createdAt(),
                fileMetadata.updatedAt()
        );
    }
}
