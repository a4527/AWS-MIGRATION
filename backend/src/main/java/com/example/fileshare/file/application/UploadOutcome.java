package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;

/** 업로드 결과다. {@code replayed}가 true면 같은 Idempotency-Key의 이전 결과를 다시 돌려준 것이다. */
public record UploadOutcome(
        FileMetadata metadata,
        boolean replayed
) {
}
