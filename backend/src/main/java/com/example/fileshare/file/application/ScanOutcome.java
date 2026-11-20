package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;

/** 검사 결과와 그에 따른 파일 상태다(FILE-03). 확인되지 않은 결과는 모두 격리한다(fail closed). */
public enum ScanOutcome {
    CLEAN(ScanStatus.CLEAN, FileStatus.AVAILABLE),
    INFECTED(ScanStatus.INFECTED, FileStatus.QUARANTINED),
    FAILED(ScanStatus.FAILED, FileStatus.QUARANTINED);

    private final ScanStatus scanStatus;
    private final FileStatus fileStatus;

    ScanOutcome(ScanStatus scanStatus, FileStatus fileStatus) {
        this.scanStatus = scanStatus;
        this.fileStatus = fileStatus;
    }

    public ScanStatus scanStatus() {
        return scanStatus;
    }

    public FileStatus fileStatus() {
        return fileStatus;
    }

    /**
     * GuardDuty Malware Protection for S3의 {@code scanResultDetails.scanResultStatus}를 바꾼다.
     * NO_THREATS_FOUND만 CLEAN이고, UNSUPPORTED·ACCESS_DENIED·FAILED 등 나머지는 FAILED다.
     */
    public static ScanOutcome fromGuardDuty(String scanResultStatus) {
        if ("NO_THREATS_FOUND".equals(scanResultStatus)) {
            return CLEAN;
        }
        if ("THREATS_FOUND".equals(scanResultStatus)) {
            return INFECTED;
        }
        return FAILED;
    }
}
