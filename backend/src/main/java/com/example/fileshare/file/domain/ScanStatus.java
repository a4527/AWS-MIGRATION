package com.example.fileshare.file.domain;

public enum ScanStatus {
    /** 검사 엔진이 없는 환경(로컬·온프레미스). 검사를 했다고 주장하지 않는다. */
    NOT_SCANNED,
    PENDING,
    CLEAN,
    INFECTED,
    FAILED
}
