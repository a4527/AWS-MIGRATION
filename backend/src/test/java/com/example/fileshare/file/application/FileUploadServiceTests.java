package com.example.fileshare.file.application;

import static com.example.fileshare.file.application.TestFixtures.IN_PROGRESS_TIMEOUT;
import static com.example.fileshare.file.application.TestFixtures.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fileshare.common.actor.Actor;
import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.file.application.TestFixtures.FaultyFileMetadataRepository;
import com.example.fileshare.file.application.TestFixtures.FaultyFileStorage;
import com.example.fileshare.file.application.TestFixtures.MutableClock;
import com.example.fileshare.file.domain.UploadRequest;
import com.example.fileshare.file.domain.UploadRequestStatus;
import com.example.fileshare.file.repository.InMemoryUploadRequestRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUploadServiceTests {

    private static final Actor OWNER = new Actor(1L, false);

    @TempDir
    Path storageRoot;

    private FaultyFileMetadataRepository files;
    private InMemoryUploadRequestRepository requests;
    private FaultyFileStorage storage;
    private MutableClock clock;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        files = new FaultyFileMetadataRepository();
        requests = new InMemoryUploadRequestRepository();
        storage = new FaultyFileStorage(storageRoot);
        clock = new MutableClock(Instant.parse("2026-11-17T00:00:00Z"));
        service = new FileUploadService(files, requests, new UploadRecorder(files, requests), storage,
                new NoOpFileMetadataCache(), TestFixtures.policy(), clock);
    }

    @Test
    void transientMetadataFailureIsRetried() throws IOException {
        files.failSaves = 2;

        UploadOutcome outcome = service.upload(OWNER, textFile("hello"), null, null);

        assertThat(outcome.metadata().id()).isNotNull();
        assertThat(storage.list()).hasSize(1);
    }

    @Test
    void persistentMetadataFailureDeletesObjectAndMarksRequestFailed() throws IOException {
        files.failSaves = Integer.MAX_VALUE;

        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, "key-1"), ErrorCode.FILE_UPLOAD_FAILED);

        assertThat(storage.list()).isEmpty();
        assertThat(requests.find(OWNER.userId(), "key-1")).get()
                .extracting(UploadRequest::status).isEqualTo(UploadRequestStatus.FAILED);
    }

    @Test
    void unknownOutcomeKeepsObjectForReconciliation() throws IOException {
        files.failSaves = Integer.MAX_VALUE;
        files.failFindByStoragePath = true;

        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, "key-1"), ErrorCode.FILE_UPLOAD_FAILED);

        assertThat(storage.list()).hasSize(1);
        assertThat(requests.find(OWNER.userId(), "key-1")).get()
                .extracting(UploadRequest::status).isEqualTo(UploadRequestStatus.IN_PROGRESS);
    }

    @Test
    void commitWithLostResponseReturnsCommittedMetadataAndKeepsObject() throws IOException {
        files.commitThenFail = true;

        UploadOutcome outcome = service.upload(OWNER, textFile("hello"), null, null);

        assertThat(files.delegate.findByStoragePath(outcome.metadata().storagePath())).isPresent();
        assertThat(storage.list()).hasSize(1);
    }

    @Test
    void lostWriteResponseDeletesObject() throws IOException {
        storage.failAfterWrite = true;

        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, null), ErrorCode.FILE_UPLOAD_FAILED);

        assertThat(storage.list()).isEmpty();
    }

    @Test
    void failedCompensationLeavesObjectForReconciliation() throws IOException {
        files.failSaves = Integer.MAX_VALUE;
        storage.failDelete = true;

        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, null), ErrorCode.FILE_UPLOAD_FAILED);

        assertThat(storage.list()).hasSize(1);
    }

    @Test
    void sameKeyAndSameRequestReplaysFirstResult() throws IOException {
        UploadOutcome first = service.upload(OWNER, textFile("hello"), "memo", "key-1");
        UploadOutcome second = service.upload(OWNER, textFile("hello"), "memo", "key-1");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.metadata().id()).isEqualTo(first.metadata().id());
        assertThat(storage.list()).hasSize(1);
        assertThat(files.findVisibleByOwnerId(OWNER.userId())).hasSize(1);
    }

    @Test
    void keysAreScopedPerUser() {
        UploadOutcome mine = service.upload(OWNER, textFile("hello"), null, "key-1");
        UploadOutcome theirs = service.upload(new Actor(2L, false), textFile("hello"), null, "key-1");

        assertThat(theirs.replayed()).isFalse();
        assertThat(theirs.metadata().id()).isNotEqualTo(mine.metadata().id());
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        service.upload(OWNER, textFile("hello"), null, "key-1");

        assertErrorCode(() -> service.upload(OWNER, textFile("other"), null, "key-1"), ErrorCode.IDEMPOTENCY_KEY_MISMATCH);
        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), "changed", "key-1"), ErrorCode.IDEMPOTENCY_KEY_MISMATCH);
    }

    @Test
    void concurrentRequestWithSameKeyGetsInProgress() throws IOException {
        AtomicReference<Throwable> concurrentFailure = new AtomicReference<>();
        storage.duringWrite = () -> {
            try {
                service.upload(OWNER, textFile("hello"), null, "key-1");
            } catch (Throwable throwable) {
                concurrentFailure.set(throwable);
            }
        };

        UploadOutcome first = service.upload(OWNER, textFile("hello"), null, "key-1");

        assertThat(first.replayed()).isFalse();
        assertThat(concurrentFailure.get()).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_IN_PROGRESS));
        assertThat(storage.list()).hasSize(1);
    }

    @Test
    void staleInProgressRequestIsTakenOver() {
        files.failSaves = Integer.MAX_VALUE;
        files.failFindByStoragePath = true;
        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, "key-1"), ErrorCode.FILE_UPLOAD_FAILED);
        files.failSaves = 0;
        files.failFindByStoragePath = false;

        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, "key-1"), ErrorCode.IDEMPOTENCY_IN_PROGRESS);

        clock.advance(IN_PROGRESS_TIMEOUT.plusMinutes(1));
        UploadOutcome outcome = service.upload(OWNER, textFile("hello"), null, "key-1");
        assertThat(outcome.replayed()).isFalse();
        assertThat(requests.find(OWNER.userId(), "key-1")).get()
                .extracting(UploadRequest::status).isEqualTo(UploadRequestStatus.COMPLETED);
    }

    @Test
    void failedRequestCanBeRetriedWithSameKey() {
        files.failSaves = Integer.MAX_VALUE;
        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, "key-1"), ErrorCode.FILE_UPLOAD_FAILED);
        files.failSaves = 0;

        UploadOutcome outcome = service.upload(OWNER, textFile("hello"), null, "key-1");

        assertThat(outcome.replayed()).isFalse();
        assertThat(outcome.metadata().id()).isNotNull();
    }

    @Test
    void expiredKeyCanBeReusedForDifferentRequest() {
        service.upload(OWNER, textFile("hello"), null, "key-1");
        clock.advance(Duration.ofHours(25));

        UploadOutcome outcome = service.upload(OWNER, textFile("other"), null, "key-1");

        assertThat(outcome.replayed()).isFalse();
    }

    @Test
    void lateCompletionOfSupersededAttemptIsRejected() {
        Instant now = clock.instant();
        UploadRequest started = requests.insert(UploadRequest.start(1L, "key-1", "hash", "old-path", now, now.plusSeconds(60)));
        UploadRequest restarted = started.restarted("hash", "new-path", now, now.plusSeconds(60));
        assertThat(requests.restart(started, restarted)).isTrue();

        assertThat(requests.complete(started.id(), "old-path", 10L, now)).isFalse();
        assertThat(requests.complete(started.id(), "new-path", 10L, now)).isTrue();
    }

    @Test
    void malformedKeyIsRejected() {
        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, "bad key with spaces"), ErrorCode.VALIDATION_ERROR);
        assertErrorCode(() -> service.upload(OWNER, textFile("hello"), null, "x".repeat(101)), ErrorCode.VALIDATION_ERROR);
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
