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
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FILE-03: 검사 결과 반영, 중복·역순·삭제·선도착·시간 초과, 다운로드 정책. */
class ScanResultServiceTests {

    @TempDir
    Path storageRoot;

    private final InMemoryFileMetadataRepository files = new InMemoryFileMetadataRepository();
    private final TestFixtures.MutableClock clock = new TestFixtures.MutableClock(Instant.parse("2026-11-20T00:00:00Z"));
    private final ScanPolicy guardduty = new ScanPolicy("guardduty", Duration.ofMinutes(15));
    private ScanResultService scans;
    private FileService fileService;
    private final Actor owner = new Actor(7L, false);

    @BeforeEach
    void setUp() {
        scans = new ScanResultService(files, new NoOpFileMetadataCache(), guardduty, clock);
        fileService = new FileService(files, new LocalFileStorage(storageRoot.toString()), new NoOpFileMetadataCache());
    }

    private FileMetadata pending(String path) {
        Instant now = clock.instant();
        return files.save(new FileMetadata(null, owner.userId(), "a.txt", path, path, 3, "text/plain",
                guardduty.initialStatus(), guardduty.initialScanStatus(), null, now, now));
    }

    @Test
    void guarddutyModeStoresNewFilesAsScanningAndBlocksDownload() {
        FileMetadata file = pending("files/a.txt");
        assertThat(file.status()).isEqualTo(FileStatus.SCANNING);
        assertThat(file.scanStatus()).isEqualTo(ScanStatus.PENDING);
        assertThatThrownBy(() -> fileService.download(owner, file.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_SCAN_PENDING);
    }

    @Test
    void noneModeNeverClaimsAScan() {
        ScanPolicy none = ScanPolicy.none();
        assertThat(none.initialStatus()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(none.initialScanStatus()).isEqualTo(ScanStatus.NOT_SCANNED);
    }

    @Test
    void cleanResultMakesFileAvailable() {
        FileMetadata file = pending("files/clean.txt");
        assertThat(scans.apply("files/clean.txt", ScanOutcome.CLEAN, "NO_THREATS_FOUND")).isEqualTo(ScanResultService.Result.APPLIED);
        FileMetadata after = files.findById(file.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(after.scanStatus()).isEqualTo(ScanStatus.CLEAN);
    }

    @Test
    void infectedAndFailedResultsQuarantineAndBlockDownload() {
        FileMetadata infected = pending("files/eicar.txt");
        FileMetadata failed = pending("files/unsupported.zip");
        scans.apply("files/eicar.txt", ScanOutcome.fromGuardDuty("THREATS_FOUND"), "THREATS_FOUND");
        scans.apply("files/unsupported.zip", ScanOutcome.fromGuardDuty("UNSUPPORTED"), "UNSUPPORTED");

        assertThat(files.findById(infected.id()).orElseThrow().scanStatus()).isEqualTo(ScanStatus.INFECTED);
        assertThat(files.findById(failed.id()).orElseThrow().scanStatus()).isEqualTo(ScanStatus.FAILED);
        for (Long id : new Long[] {infected.id(), failed.id()}) {
            assertThatThrownBy(() -> fileService.download(owner, id))
                    .extracting(e -> ((BusinessException) e).errorCode())
                    .isEqualTo(ErrorCode.FILE_QUARANTINED);
        }
    }

    @Test
    void unknownGuardDutyStatusesFailClosed() {
        assertThat(ScanOutcome.fromGuardDuty("ACCESS_DENIED")).isEqualTo(ScanOutcome.FAILED);
        assertThat(ScanOutcome.fromGuardDuty("FAILED")).isEqualTo(ScanOutcome.FAILED);
        assertThat(ScanOutcome.fromGuardDuty("SOMETHING_NEW")).isEqualTo(ScanOutcome.FAILED);
    }

    @Test
    void duplicateAndLateResultsDoNotChangeAFinalVerdict() {
        FileMetadata file = pending("files/x.txt");
        scans.apply("files/x.txt", ScanOutcome.INFECTED, "THREATS_FOUND");

        assertThat(scans.apply("files/x.txt", ScanOutcome.INFECTED, "duplicate")).isEqualTo(ScanResultService.Result.IGNORED);
        assertThat(scans.apply("files/x.txt", ScanOutcome.CLEAN, "late clean")).isEqualTo(ScanResultService.Result.IGNORED);
        assertThat(files.findById(file.id()).orElseThrow().scanStatus()).isEqualTo(ScanStatus.INFECTED);
    }

    @Test
    void resultForDeletedFileDoesNotRestoreIt() {
        FileMetadata file = pending("files/deleted.txt");
        fileService.delete(owner, file.id());

        assertThat(scans.apply("files/deleted.txt", ScanOutcome.CLEAN, "NO_THREATS_FOUND")).isEqualTo(ScanResultService.Result.IGNORED);
        assertThat(files.findById(file.id()).orElseThrow().status()).isEqualTo(FileStatus.DELETED);
        assertThatThrownBy(() -> fileService.get(owner, file.id()))
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void resultBeforeTheRowExistsAsksForRetryThenAppliesLater() {
        assertThat(scans.apply("files/early.txt", ScanOutcome.CLEAN, "early")).isEqualTo(ScanResultService.Result.NOT_FOUND);
        FileMetadata file = pending("files/early.txt");
        assertThat(scans.apply("files/early.txt", ScanOutcome.CLEAN, "retry")).isEqualTo(ScanResultService.Result.APPLIED);
        assertThat(files.findById(file.id()).orElseThrow().status()).isEqualTo(FileStatus.AVAILABLE);
    }

    @Test
    void pendingPastTimeoutIsQuarantinedButRecentOnesWait() {
        FileMetadata old = pending("files/old.txt");
        clock.advance(Duration.ofMinutes(10));
        FileMetadata recent = pending("files/recent.txt");
        clock.advance(Duration.ofMinutes(6));

        assertThat(scans.failTimedOut()).isEqualTo(1);
        assertThat(files.findById(old.id()).orElseThrow().scanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(files.findById(old.id()).orElseThrow().status()).isEqualTo(FileStatus.QUARANTINED);
        assertThat(files.findById(recent.id()).orElseThrow().scanStatus()).isEqualTo(ScanStatus.PENDING);
        // A result that arrives after the timeout does not override the quarantine.
        assertThat(scans.apply("files/old.txt", ScanOutcome.CLEAN, "late")).isEqualTo(ScanResultService.Result.IGNORED);
    }
}
