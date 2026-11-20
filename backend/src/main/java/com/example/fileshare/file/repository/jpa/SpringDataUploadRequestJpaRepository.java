package com.example.fileshare.file.repository.jpa;

import com.example.fileshare.file.domain.UploadRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataUploadRequestJpaRepository extends JpaRepository<UploadRequestEntity, Long> {

    Optional<UploadRequestEntity> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    List<UploadRequestEntity> findByStatusAndUpdatedAtBefore(UploadRequestStatus status, Instant threshold);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UploadRequestEntity u
               set u.status = :status, u.requestHash = :requestHash, u.storagePath = :storagePath, u.fileId = null,
                   u.createdAt = :now, u.updatedAt = :now, u.expiresAt = :expiresAt, u.version = u.version + 1
             where u.id = :id and u.version = :version
            """)
    int restart(
            @Param("id") Long id,
            @Param("version") long version,
            @Param("status") UploadRequestStatus status,
            @Param("requestHash") String requestHash,
            @Param("storagePath") String storagePath,
            @Param("now") Instant now,
            @Param("expiresAt") Instant expiresAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UploadRequestEntity u
               set u.status = :newStatus, u.fileId = :fileId, u.updatedAt = :now, u.version = u.version + 1
             where u.id = :id and u.status = :expectedStatus and u.storagePath = :storagePath
            """)
    int transition(
            @Param("id") Long id,
            @Param("storagePath") String storagePath,
            @Param("expectedStatus") UploadRequestStatus expectedStatus,
            @Param("newStatus") UploadRequestStatus newStatus,
            @Param("fileId") Long fileId,
            @Param("now") Instant now
    );

    @Modifying
    @Query("delete from UploadRequestEntity u where u.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
