package com.example.fileshare.file.repository.jpa;

import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Query;

import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;

import com.example.fileshare.file.domain.ScanStatus;

import com.example.fileshare.file.domain.FileStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataFileMetadataJpaRepository extends JpaRepository<FileMetadataEntity, Long> {

    List<FileMetadataEntity> findByOwnerIdAndStatusNotOrderByCreatedAtDesc(Long ownerId, FileStatus status);

    Optional<FileMetadataEntity> findFirstByStoragePath(String storagePath);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FileMetadataEntity f
               set f.scanStatus = :scanStatus, f.status = :status, f.updatedAt = :at
             where f.storagePath = :storagePath
               and f.scanStatus = com.example.fileshare.file.domain.ScanStatus.PENDING
               and f.status <> com.example.fileshare.file.domain.FileStatus.DELETED
            """)
    int applyScanResult(@Param("storagePath") String storagePath,
                        @Param("scanStatus") ScanStatus scanStatus,
                        @Param("status") FileStatus status,
                        @Param("at") Instant at);

    @Query("""
            select f.storagePath from FileMetadataEntity f
             where f.scanStatus = com.example.fileshare.file.domain.ScanStatus.PENDING
               and f.status <> com.example.fileshare.file.domain.FileStatus.DELETED
               and f.createdAt < :before
            """)
    List<String> findPendingScanBefore(@Param("before") Instant before);
}
