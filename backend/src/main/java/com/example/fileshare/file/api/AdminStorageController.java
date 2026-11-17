package com.example.fileshare.file.api;

import com.example.fileshare.common.api.ApiResponse;
import com.example.fileshare.file.application.StorageReconciliationService;
import com.example.fileshare.file.application.StorageReconciliationService.CleanupResult;
import com.example.fileshare.file.application.StorageReconciliationService.ReconciliationReport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자용 저장소 정합성 점검 API다. */
@RestController
@RequestMapping("/api/admin/storage")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStorageController {

    private static final String DEFAULT_MIN_AGE_MINUTES = "60";

    private final StorageReconciliationService reconciliationService;

    public AdminStorageController(StorageReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/orphans")
    public ApiResponse<OrphanReportResponse> scan(
            @RequestParam(defaultValue = DEFAULT_MIN_AGE_MINUTES) long minAgeMinutes
    ) {
        return ApiResponse.ok(OrphanReportResponse.from(reconciliationService.scan(Duration.ofMinutes(minAgeMinutes))));
    }

    @PostMapping("/orphans/cleanup")
    public ApiResponse<CleanupResult> cleanup(
            @RequestParam(defaultValue = DEFAULT_MIN_AGE_MINUTES) long minAgeMinutes
    ) {
        return ApiResponse.ok(reconciliationService.cleanup(Duration.ofMinutes(minAgeMinutes)), "Orphan cleanup finished");
    }

    public record OrphanReportResponse(
            Instant checkedAt,
            long minAgeMinutes,
            List<OrphanObject> orphanObjects,
            List<StaleUploadRequest> staleUploadRequests
    ) {

        static OrphanReportResponse from(ReconciliationReport report) {
            return new OrphanReportResponse(
                    report.checkedAt(),
                    report.minAge().toMinutes(),
                    report.orphanObjects().stream()
                            .map(entry -> new OrphanObject(entry.storagePath(), entry.size(), entry.lastModified()))
                            .toList(),
                    report.staleUploadRequests().stream()
                            .map(request -> new StaleUploadRequest(request.id(), request.ownerId(),
                                    request.storagePath(), request.updatedAt()))
                            .toList()
            );
        }
    }

    public record OrphanObject(String storagePath, long size, Instant lastModified) {
    }

    public record StaleUploadRequest(Long uploadRequestId, Long ownerId, String storagePath, Instant updatedAt) {
    }
}
