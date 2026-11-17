package com.example.fileshare.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.file.application.StorageReconciliationService.CleanupResult;
import com.example.fileshare.file.application.StorageReconciliationService.ReconciliationReport;
import com.example.fileshare.file.application.TestFixtures.MutableClock;
import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.example.fileshare.file.domain.UploadRequest;
import com.example.fileshare.file.repository.InMemoryFileMetadataRepository;
import com.example.fileshare.file.repository.InMemoryUploadRequestRepository;
import com.example.fileshare.infra.storage.LocalFileStorage;
import com.example.fileshare.infra.storage.StorageEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageReconciliationServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @TempDir
    Path root;

    private InMemoryFileMetadataRepository files;
    private InMemoryUploadRequestRepository requests;
    private LocalFileStorage storage;
    private StorageReconciliationService service;

    @BeforeEach
    void setUp() {
        files = new InMemoryFileMetadataRepository();
        requests = new InMemoryUploadRequestRepository();
        storage = new LocalFileStorage(root.toString());
        service = new StorageReconciliationService(storage, files, requests, TestFixtures.policy(), new MutableClock(NOW));
    }

    @Test
    void reportsOnlyOldUnreferencedObjects() throws IOException {
        Path referenced = object("referenced.txt", Duration.ofHours(3));
        Path deletedButKept = object("deleted.txt", Duration.ofHours(3));
        Path orphan = object("orphan.txt", Duration.ofHours(3));
        object("young-orphan.txt", Duration.ofMinutes(5));
        files.save(metadata(referenced, FileStatus.AVAILABLE));
        files.save(metadata(deletedButKept, FileStatus.DELETED));
        requests.insert(UploadRequest.start(1L, "stale", "hash", orphan.toString(),
                NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(23))));

        ReconciliationReport report = service.scan(Duration.ofHours(1));

        assertThat(report.orphanObjects()).extracting(StorageEntry::storagePath).containsExactly(orphan.toString());
        assertThat(report.staleUploadRequests()).extracting(UploadRequest::storagePath).containsExactly(orphan.toString());
    }

    @Test
    void cleanupDeletesOnlyOrphans() throws IOException {
        Path referenced = object("referenced.txt", Duration.ofHours(3));
        Path orphan = object("orphan.txt", Duration.ofHours(3));
        files.save(metadata(referenced, FileStatus.AVAILABLE));

        CleanupResult result = service.cleanup(Duration.ofHours(1));

        assertThat(result.deletedObjects()).containsExactly(orphan.toString());
        assertThat(Files.exists(orphan)).isFalse();
        assertThat(Files.exists(referenced)).isTrue();
    }

    @Test
    void minimumAgeShorterThanInProgressTimeoutIsRejected() {
        assertThatThrownBy(() -> service.scan(Duration.ofMinutes(5)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private Path object(String name, Duration age) throws IOException {
        Path path = root.resolve(name);
        Files.writeString(path, name);
        Files.setLastModifiedTime(path, FileTime.from(NOW.minus(age)));
        return path;
    }

    private FileMetadata metadata(Path path, FileStatus status) {
        FileMetadata metadata = new FileMetadata(null, 1L, path.getFileName().toString(), path.getFileName().toString(),
                path.toString(), 1, "text/plain", FileStatus.AVAILABLE, ScanStatus.CLEAN, null, NOW, NOW);
        if (status == FileStatus.DELETED) {
            metadata.markDeleted();
        }
        return metadata;
    }
}
