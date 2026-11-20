package com.example.fileshare.file.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 검사 결과가 끝내 오지 않은 파일을 격리한다(FILE-03). 미검사 파일이 정상으로 남지 않게 한다. */
@Component
@ConditionalOnProperty(name = "app.file.scan.mode", havingValue = "guardduty")
public class ScanTimeoutJob {

    private final ScanResultService scanResultService;

    public ScanTimeoutJob(ScanResultService scanResultService) {
        this.scanResultService = scanResultService;
    }

    @Scheduled(fixedDelayString = "${app.file.scan.timeout-check-interval:PT1M}",
            initialDelayString = "${app.file.scan.timeout-check-interval:PT1M}")
    public void run() {
        scanResultService.failTimedOut();
    }
}
