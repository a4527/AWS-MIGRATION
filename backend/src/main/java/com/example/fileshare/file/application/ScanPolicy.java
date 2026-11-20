package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 업로드 파일의 악성코드 검사 방식이다(FILE-03, DEC-018).
 *
 * <ul>
 *   <li>{@code none}: 검사 엔진이 없는 환경(로컬·온프레미스). {@code AVAILABLE}/{@code NOT_SCANNED}로 저장해 바로 내려받을 수
 *       있지만, 검사했다고 주장하지 않는다.</li>
 *   <li>{@code guardduty}: AWS. GuardDuty Malware Protection for S3가 검사하고 결과가 SQS로 온다. {@code SCANNING}/{@code PENDING}으로
 *       저장하고 결과가 {@code CLEAN}일 때만 내려받을 수 있다.</li>
 * </ul>
 */
@Component
public class ScanPolicy {

    public enum Mode { NONE, GUARDDUTY }

    private final Mode mode;
    private final Duration timeout;

    public ScanPolicy(
            @Value("${app.file.scan.mode:none}") String mode,
            @Value("${app.file.scan.timeout:PT15M}") Duration timeout
    ) {
        this.mode = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        this.timeout = timeout;
    }

    public static ScanPolicy none() {
        return new ScanPolicy("none", Duration.ofMinutes(15));
    }

    public Mode mode() {
        return mode;
    }

    public boolean asynchronous() {
        return mode != Mode.NONE;
    }

    public FileStatus initialStatus() {
        return asynchronous() ? FileStatus.SCANNING : FileStatus.AVAILABLE;
    }

    public ScanStatus initialScanStatus() {
        return asynchronous() ? ScanStatus.PENDING : ScanStatus.NOT_SCANNED;
    }

    /** 결과가 이 시간 안에 오지 않으면 FAILED로 격리한다. */
    public Duration timeout() {
        return timeout;
    }
}
