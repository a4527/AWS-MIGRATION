package com.example.fileshare.file.repository.jpa;

import com.example.fileshare.file.domain.UploadRequest;
import com.example.fileshare.file.domain.UploadRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "upload_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_upload_requests_owner_key", columnNames = {"owner_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_upload_requests_status_updated_at", columnList = "status, updated_at"),
                @Index(name = "idx_upload_requests_expires_at", columnList = "expires_at")
        }
)
public class UploadRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadRequestStatus status;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private long version;

    protected UploadRequestEntity() {
    }

    static UploadRequestEntity from(UploadRequest request) {
        UploadRequestEntity entity = new UploadRequestEntity();
        entity.id = request.id();
        entity.ownerId = request.ownerId();
        entity.idempotencyKey = request.idempotencyKey();
        entity.requestHash = request.requestHash();
        entity.status = request.status();
        entity.storagePath = request.storagePath();
        entity.fileId = request.fileId();
        entity.createdAt = request.createdAt();
        entity.updatedAt = request.updatedAt();
        entity.expiresAt = request.expiresAt();
        entity.version = request.version();
        return entity;
    }

    UploadRequest toDomain() {
        return new UploadRequest(id, ownerId, idempotencyKey, requestHash, status, storagePath, fileId,
                createdAt, updatedAt, expiresAt, version);
    }
}
