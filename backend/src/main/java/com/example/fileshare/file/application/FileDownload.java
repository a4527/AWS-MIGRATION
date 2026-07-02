package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;
import org.springframework.core.io.Resource;

public record FileDownload(
        FileMetadata metadata,
        Resource resource,
        long contentLength
) {
}
