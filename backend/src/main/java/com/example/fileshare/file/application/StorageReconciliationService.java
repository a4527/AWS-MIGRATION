package com.example.fileshare.file.application;

import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.file.domain.UploadRequest;
import com.example.fileshare.file.repository.FileMetadataRepository;
import com.example.fileshare.file.repository.UploadRequestRepository;
import com.example.fileshare.infra.storage.FileStorage;
import com.example.fileshare.infra.storage.StorageEntry;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 저장소 객체와 DB 메타데이터의 정합성을 점검한다.
 *
 * <p>어떤 {@code files} 행(논리 삭제 포함)도 가리키지 않는 객체를 고아 객체로 본다. 진행 중인 업로드를 건드리지 않도록
 * 최소 경과 시간보다 오래된 객체만 대상으로 하며, 이 시간은 진행 중 판단 기준보다 짧을 수 없다.
 */
@Service
public class StorageReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(StorageReconciliationService.class);

    private final FileStorage fileStorage;
    private final FileMetadataRepository fileMetadataRepository;
    private final UploadRequestRepository uploadRequestRepository;
    private final UploadPolicy policy;
    private final Clock clock;

    public StorageReconciliationService(
            FileStorage fileStorage,
            FileMetadataRepository fileMetadataRepository,
            UploadRequestRepository uploadRequestRepository,
            UploadPolicy policy,
            Clock clock
    ) {
        this.fileStorage = fileStorage;
        this.fileMetadataRepository = fileMetadataRepository;
        this.uploadRequestRepository = uploadRequestRepository;
        this.policy = policy;
        this.clock = clock;
    }

    public ReconciliationReport scan(Duration minAge) {
        validateMinAge(minAge);
        Instant now = clock.instant();
        List<StorageEntry> orphans = findOrphans(now.minus(minAge));
        List<UploadRequest> staleRequests = uploadRequestRepository.findInProgressUpdatedBefore(
                now.minus(policy.inProgressTimeout()));
        return new ReconciliationReport(now, minAge, orphans, staleRequests);
    }

    public CleanupResult cleanup(Duration minAge) {
        validateMinAge(minAge);
        List<String> deleted = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (StorageEntry orphan : findOrphans(clock.instant().minus(minAge))) {
            // 나열과 삭제 사이에 메타데이터가 생겼을 수 있으므로 지우기 직전에 다시 확인한다.
            if (fileMetadataRepository.findByStoragePath(orphan.storagePath()).isPresent()) {
                continue;
            }
            try {
                fileStorage.delete(orphan.storagePath());
                deleted.add(orphan.storagePath());
                log.info("Orphan object deleted. storagePath={}, size={}", orphan.storagePath(), orphan.size());
            } catch (IOException | RuntimeException exception) {
                failed.add(orphan.storagePath());
                log.error("Orphan object delete failed. storagePath={}", orphan.storagePath(), exception);
            }
        }
        return new CleanupResult(deleted, failed);
    }

    private List<StorageEntry> findOrphans(Instant createdBefore) {
        List<StorageEntry> entries;
        try {
            entries = fileStorage.list();
        } catch (IOException exception) {
            log.error("Storage listing failed during reconciliation", exception);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return entries.stream()
                .filter(entry -> entry.lastModified().isBefore(createdBefore))
                .filter(entry -> fileMetadataRepository.findByStoragePath(entry.storagePath()).isEmpty())
                .toList();
    }

    private void validateMinAge(Duration minAge) {
        if (minAge == null || minAge.compareTo(policy.inProgressTimeout()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    public record ReconciliationReport(
            Instant checkedAt,
            Duration minAge,
            List<StorageEntry> orphanObjects,
            List<UploadRequest> staleUploadRequests
    ) {
    }

    public record CleanupResult(
            List<String> deletedObjects,
            List<String> failedObjects
    ) {
    }
}
