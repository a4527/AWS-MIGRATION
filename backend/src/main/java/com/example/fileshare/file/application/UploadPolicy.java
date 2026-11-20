package com.example.fileshare.file.application;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 업로드 검증·멱등성·재시도 설정이다. */
@Component
public class UploadPolicy {

    private final long maxSizeBytes;
    private final Set<String> allowedExtensions;
    private final Duration idempotencyTtl;
    private final Duration inProgressTimeout;
    private final int recordAttempts;
    private final Duration recordBackoff;

    public UploadPolicy(
            @Value("${app.file.max-size-bytes}") long maxSizeBytes,
            @Value("${app.file.allowed-extensions}") String allowedExtensions,
            @Value("${app.file.idempotency.ttl:PT24H}") Duration idempotencyTtl,
            @Value("${app.file.idempotency.in-progress-timeout:PT15M}") Duration inProgressTimeout,
            @Value("${app.file.upload.record-attempts:3}") int recordAttempts,
            @Value("${app.file.upload.record-backoff:PT0.1S}") Duration recordBackoff
    ) {
        this.maxSizeBytes = maxSizeBytes;
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
                .map(extension -> extension.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toUnmodifiableSet());
        this.idempotencyTtl = idempotencyTtl;
        this.inProgressTimeout = inProgressTimeout;
        this.recordAttempts = Math.max(1, recordAttempts);
        this.recordBackoff = recordBackoff;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }

    public boolean isAllowedExtension(String extension) {
        return allowedExtensions.contains(extension);
    }

    public Duration idempotencyTtl() {
        return idempotencyTtl;
    }

    public Duration inProgressTimeout() {
        return inProgressTimeout;
    }

    public int recordAttempts() {
        return recordAttempts;
    }

    public Duration recordBackoff() {
        return recordBackoff;
    }
}
