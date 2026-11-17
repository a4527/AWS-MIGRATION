package com.example.fileshare.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fileshare.common.actor.Actor;
import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.example.fileshare.file.repository.InMemoryFileMetadataRepository;
import com.example.fileshare.infra.storage.LocalFileStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileServiceCacheTests {

    private static final Actor OWNER = new Actor(1L, false);

    @TempDir
    Path storageRoot;

    private InMemoryFileMetadataRepository files;
    private RecordingCache cache;
    private FileService service;

    @BeforeEach
    void setUp() {
        files = new InMemoryFileMetadataRepository();
        cache = new RecordingCache();
        service = new FileService(files, new LocalFileStorage(storageRoot.toString()), cache);
    }

    @Test
    void downloadAndDeleteIgnoreStaleCacheAndUseDatabase() {
        FileMetadata stored = files.save(metadata(FileStatus.AVAILABLE));
        cache.entries.put(stored.id(), copy(stored));
        stored.markDeleted();
        files.save(stored);

        assertNotFound(() -> service.download(OWNER, stored.id()));
        assertNotFound(() -> service.delete(OWNER, stored.id()));
    }

    @Test
    void deleteWritesDeletedStateWithRetryingPut() {
        FileMetadata stored = files.save(metadata(FileStatus.AVAILABLE));

        service.delete(OWNER, stored.id());

        assertThat(cache.latestWrites).singleElement()
                .extracting(FileMetadata::status).isEqualTo(FileStatus.DELETED);
    }

    @Test
    void cacheHitIsNotWrittenBackAndMissIsFilledFromDatabase() {
        FileMetadata stored = files.save(metadata(FileStatus.AVAILABLE));

        service.get(OWNER, stored.id());
        assertThat(cache.puts).hasSize(1);

        cache.entries.put(stored.id(), copy(stored));
        service.get(OWNER, stored.id());
        assertThat(cache.puts).hasSize(1);
    }

    @Test
    void deletedEntryInCacheHidesFileFromMetadataLookup() {
        FileMetadata stored = files.save(metadata(FileStatus.AVAILABLE));
        service.delete(OWNER, stored.id());
        cache.entries.put(stored.id(), cache.latestWrites.get(0));

        assertNotFound(() -> service.get(OWNER, stored.id()));
    }

    private void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private FileMetadata metadata(FileStatus status) {
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        return new FileMetadata(null, OWNER.userId(), "note.txt", "a.txt", storageRoot.resolve("a.txt").toString(),
                5, "text/plain", status, ScanStatus.CLEAN, null, now, now);
    }

    private FileMetadata copy(FileMetadata source) {
        return new FileMetadata(source.id(), source.ownerId(), source.originalFileName(), source.storedFileName(),
                source.storagePath(), source.size(), source.mimeType(), source.status(), source.scanStatus(),
                source.description(), source.createdAt(), source.updatedAt());
    }

    private static final class RecordingCache implements FileMetadataCache {

        final Map<Long, FileMetadata> entries = new HashMap<>();
        final List<FileMetadata> puts = new ArrayList<>();
        final List<FileMetadata> latestWrites = new ArrayList<>();

        @Override
        public Optional<FileMetadata> findById(Long id) {
            return Optional.ofNullable(entries.get(id));
        }

        @Override
        public void put(FileMetadata fileMetadata) {
            puts.add(fileMetadata);
        }

        @Override
        public void putLatest(FileMetadata fileMetadata) {
            latestWrites.add(fileMetadata);
        }
    }
}
