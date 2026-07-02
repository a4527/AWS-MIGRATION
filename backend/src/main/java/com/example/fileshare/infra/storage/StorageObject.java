package com.example.fileshare.infra.storage;

import org.springframework.core.io.Resource;

public record StorageObject(
        Resource resource,
        long contentLength
) {
}
