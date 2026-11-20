package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.ScanStatus;

import com.example.fileshare.file.domain.FileStatus;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.repository.FileMetadataRepository;
import com.example.fileshare.file.repository.InMemoryFileMetadataRepository;
import com.example.fileshare.infra.storage.FileStorage;
import com.example.fileshare.infra.storage.LocalFileStorage;
import com.example.fileshare.infra.storage.StorageEntry;
import com.example.fileshare.infra.storage.StorageObject;
import com.example.fileshare.infra.storage.StoredFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.mock.web.MockMultipartFile;

final class TestFixtures {

    static final Duration IN_PROGRESS_TIMEOUT = Duration.ofMinutes(15);

    private TestFixtures() {
    }

    static UploadPolicy policy() {
        return new UploadPolicy(1024, "txt", Duration.ofHours(24), IN_PROGRESS_TIMEOUT, 3, Duration.ZERO);
    }

    static MockMultipartFile textFile(String content) {
        return new MockMultipartFile("file", "note.txt", "text/plain", content.getBytes());
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** 저장 실패·커밋 후 예외·조회 실패를 주입할 수 있는 메타데이터 저장소다. */
    static final class FaultyFileMetadataRepository implements FileMetadataRepository {

        final InMemoryFileMetadataRepository delegate = new InMemoryFileMetadataRepository();
        int failSaves;
        boolean commitThenFail;
        boolean failFindByStoragePath;

        @Override
        public FileMetadata save(FileMetadata fileMetadata) {
            if (commitThenFail) {
                delegate.save(fileMetadata);
                throw new IllegalStateException("connection lost after commit");
            }
            if (failSaves > 0) {
                failSaves--;
                throw new IllegalStateException("database unavailable");
            }
            return delegate.save(fileMetadata);
        }

        @Override
        public Optional<FileMetadata> findById(Long id) {
            return delegate.findById(id);
        }

        @Override
        public List<FileMetadata> findVisibleByOwnerId(Long ownerId) {
            return delegate.findVisibleByOwnerId(ownerId);
        }

        @Override
        public Optional<FileMetadata> findByStoragePath(String storagePath) {
            if (failFindByStoragePath) {
                throw new IllegalStateException("database unavailable");
            }
            return delegate.findByStoragePath(storagePath);
        }

        @Override
        public int applyScanResult(String storagePath, ScanStatus scanStatus, FileStatus status, Instant at) {
            return delegate.applyScanResult(storagePath, scanStatus, status, at);
        }

        @Override
        public List<String> findPendingScanBefore(Instant before) {
            return delegate.findPendingScanBefore(before);
        }
    }

    /** 쓰기 후 응답 유실·삭제 실패·쓰기 중 동작을 주입할 수 있는 로컬 저장소다. */
    static final class FaultyFileStorage implements FileStorage {

        final LocalFileStorage delegate;
        boolean failAfterWrite;
        boolean failDelete;
        Runnable duringWrite;

        FaultyFileStorage(Path root) {
            this.delegate = new LocalFileStorage(root.toString());
        }

        @Override
        public StoredFile allocate(String originalFileName) {
            return delegate.allocate(originalFileName);
        }

        @Override
        public void write(StoredFile target, InputStream inputStream, long size, String contentType) throws IOException {
            if (duringWrite != null) {
                Runnable action = duringWrite;
                duringWrite = null;
                action.run();
            }
            delegate.write(target, inputStream, size, contentType);
            if (failAfterWrite) {
                throw new IOException("response lost after write");
            }
        }

        @Override
        public StorageObject load(String storagePath) throws IOException {
            return delegate.load(storagePath);
        }

        @Override
        public void delete(String storagePath) throws IOException {
            if (failDelete) {
                throw new IOException("storage unavailable");
            }
            delegate.delete(storagePath);
        }

        @Override
        public List<StorageEntry> list() throws IOException {
            return delegate.list();
        }
    }
}
