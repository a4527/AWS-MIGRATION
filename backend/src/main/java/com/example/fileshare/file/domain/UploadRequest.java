package com.example.fileshare.file.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Idempotency-Key 하나에 대응하는 업로드 요청 기록이다.
 *
 * <p>객체를 쓰기 전에 IN_PROGRESS로 저장 위치와 함께 남기고, 메타데이터 저장과 같은 트랜잭션에서 COMPLETED로 바꾼다.
 */
public record UploadRequest(
        Long id,
        Long ownerId,
        String idempotencyKey,
        String requestHash,
        UploadRequestStatus status,
        String storagePath,
        Long fileId,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        long version
) {

    public static UploadRequest start(
            Long ownerId,
            String idempotencyKey,
            String requestHash,
            String storagePath,
            Instant now,
            Instant expiresAt
    ) {
        return new UploadRequest(null, ownerId, idempotencyKey, requestHash, UploadRequestStatus.IN_PROGRESS,
                storagePath, null, now, now, expiresAt, 0);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isStale(Instant now, Duration inProgressTimeout) {
        return status == UploadRequestStatus.IN_PROGRESS && updatedAt.plus(inProgressTimeout).isBefore(now);
    }

    public UploadRequest withId(Long newId) {
        return new UploadRequest(newId, ownerId, idempotencyKey, requestHash, status, storagePath, fileId,
                createdAt, updatedAt, expiresAt, version);
    }

    public UploadRequest restarted(String newRequestHash, String newStoragePath, Instant now, Instant newExpiresAt) {
        return new UploadRequest(id, ownerId, idempotencyKey, newRequestHash, UploadRequestStatus.IN_PROGRESS,
                newStoragePath, null, now, now, newExpiresAt, version + 1);
    }

    public UploadRequest completed(Long completedFileId, Instant now) {
        return new UploadRequest(id, ownerId, idempotencyKey, requestHash, UploadRequestStatus.COMPLETED,
                storagePath, completedFileId, createdAt, now, expiresAt, version + 1);
    }

    public UploadRequest failed(Instant now) {
        return new UploadRequest(id, ownerId, idempotencyKey, requestHash, UploadRequestStatus.FAILED,
                storagePath, null, createdAt, now, expiresAt, version + 1);
    }
}
