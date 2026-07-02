package com.example.fileshare.file.api;

import com.example.fileshare.file.domain.FileMetadata;

public record FileDeleteResponse(
        Long fileId,
        String status
) {

    public static FileDeleteResponse from(FileMetadata fileMetadata) {
        return new FileDeleteResponse(fileMetadata.id(), fileMetadata.status().name().toLowerCase());
    }
}
