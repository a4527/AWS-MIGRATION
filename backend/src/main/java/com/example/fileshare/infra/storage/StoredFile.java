package com.example.fileshare.infra.storage;

public record StoredFile(
        String storedFileName,
        String storagePath
) {
}
