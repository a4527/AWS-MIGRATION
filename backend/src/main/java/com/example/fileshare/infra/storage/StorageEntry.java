package com.example.fileshare.infra.storage;

import java.time.Instant;

public record StorageEntry(
        String storagePath,
        long size,
        Instant lastModified
) {
}
