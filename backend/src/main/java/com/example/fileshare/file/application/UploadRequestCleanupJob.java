package com.example.fileshare.file.application;

import com.example.fileshare.file.repository.UploadRequestRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 보관 기간(기본 24시간)이 지난 업로드 요청 기록을 지운다. 여러 인스턴스가 동시에 실행해도 결과는 같다. */
@Component
public class UploadRequestCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(UploadRequestCleanupJob.class);

    private final UploadRequestRepository uploadRequestRepository;
    private final Clock clock;

    public UploadRequestCleanupJob(UploadRequestRepository uploadRequestRepository, Clock clock) {
        this.uploadRequestRepository = uploadRequestRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.file.idempotency.cleanup-interval:PT1H}",
            initialDelayString = "${app.file.idempotency.cleanup-interval:PT1H}")
    public void deleteExpired() {
        try {
            int deleted = uploadRequestRepository.deleteExpired(clock.instant());
            if (deleted > 0) {
                log.info("Expired upload requests deleted. count={}", deleted);
            }
        } catch (RuntimeException exception) {
            log.warn("Expired upload request cleanup failed; will retry on next run", exception);
        }
    }
}
