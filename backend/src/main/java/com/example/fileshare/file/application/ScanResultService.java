package com.example.fileshare.file.application;

import com.example.fileshare.file.repository.FileMetadataRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 검사 결과를 DB와 캐시에 반영한다(FILE-03).
 *
 * <p>DB는 PENDING인 행만 조건부로 바꾸므로 같은 결과가 두 번 오거나(중복), 늦게 온 결과가 이미 끝난 판정을 덮으려 하거나(역순),
 * 삭제된 파일에 결과가 와도 상태가 바뀌지 않는다. 행이 아직 없으면(업로드 DB 기록 전에 결과가 먼저 도착) NOT_FOUND를 돌려주고
 * 호출자가 다시 시도하게 한다.
 */
@Service
public class ScanResultService {

    private static final Logger log = LoggerFactory.getLogger(ScanResultService.class);

    public enum Result { APPLIED, IGNORED, NOT_FOUND }

    private final FileMetadataRepository fileMetadataRepository;
    private final FileMetadataCache fileMetadataCache;
    private final ScanPolicy scanPolicy;
    private final Clock clock;

    public ScanResultService(
            FileMetadataRepository fileMetadataRepository,
            FileMetadataCache fileMetadataCache,
            ScanPolicy scanPolicy,
            Clock clock
    ) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileMetadataCache = fileMetadataCache;
        this.scanPolicy = scanPolicy;
        this.clock = clock;
    }

    public Result apply(String storagePath, ScanOutcome outcome, String reason) {
        int updated = fileMetadataRepository.applyScanResult(
                storagePath, outcome.scanStatus(), outcome.fileStatus(), clock.instant());
        if (updated > 0) {
            fileMetadataRepository.findByStoragePath(storagePath).ifPresent(fileMetadataCache::putLatest);
            log.info("Scan result applied. storagePath={}, outcome={}, reason={}", storagePath, outcome, reason);
            return Result.APPLIED;
        }
        if (fileMetadataRepository.findByStoragePath(storagePath).isEmpty()) {
            log.warn("Scan result for unknown object; will retry. storagePath={}, outcome={}", storagePath, outcome);
            return Result.NOT_FOUND;
        }
        log.info("Scan result ignored (already final or deleted). storagePath={}, outcome={}", storagePath, outcome);
        return Result.IGNORED;
    }

    /** 시간 안에 결과가 오지 않은 파일을 FAILED로 격리한다. 격리한 개수를 돌려준다. */
    public int failTimedOut() {
        Instant before = clock.instant().minus(scanPolicy.timeout());
        List<String> stale = fileMetadataRepository.findPendingScanBefore(before);
        int failed = 0;
        for (String storagePath : stale) {
            if (apply(storagePath, ScanOutcome.FAILED, "scan_timeout") == Result.APPLIED) {
                failed++;
            }
        }
        if (failed > 0) {
            log.warn("Scan timed out; quarantined {} file(s) older than {}", failed, scanPolicy.timeout());
        }
        return failed;
    }
}
