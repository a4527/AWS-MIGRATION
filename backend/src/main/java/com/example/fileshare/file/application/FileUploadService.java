package com.example.fileshare.file.application;

import com.example.fileshare.common.actor.Actor;
import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.file.application.UploadRecorder.UploadRequestSupersededException;
import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.example.fileshare.file.domain.UploadRequest;
import com.example.fileshare.file.repository.DuplicateUploadRequestException;
import com.example.fileshare.file.repository.FileMetadataRepository;
import com.example.fileshare.file.repository.UploadRequestRepository;
import com.example.fileshare.infra.storage.FileStorage;
import com.example.fileshare.infra.storage.StoredFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드를 처리한다.
 *
 * <p>순서: 검증 → 저장 위치 확정 → (키가 있으면) 업로드 요청 선점 → 객체 쓰기 → 메타데이터 기록(재시도) → 캐시 반영.
 * 객체를 먼저 쓰므로 DB 기록이 실패하면 결과가 확실할 때만 객체를 지운다. 결과를 알 수 없으면 객체를 남기고
 * 정합성 점검({@link StorageReconciliationService})에 맡긴다.
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");

    private final FileMetadataRepository fileMetadataRepository;
    private final UploadRequestRepository uploadRequestRepository;
    private final UploadRecorder uploadRecorder;
    private final FileStorage fileStorage;
    private final FileMetadataCache fileMetadataCache;
    private final UploadPolicy policy;
    private final ScanPolicy scanPolicy;
    private final Clock clock;

    public FileUploadService(
            FileMetadataRepository fileMetadataRepository,
            UploadRequestRepository uploadRequestRepository,
            UploadRecorder uploadRecorder,
            FileStorage fileStorage,
            FileMetadataCache fileMetadataCache,
            UploadPolicy policy,
            ScanPolicy scanPolicy,
            Clock clock
    ) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.uploadRequestRepository = uploadRequestRepository;
        this.uploadRecorder = uploadRecorder;
        this.fileStorage = fileStorage;
        this.fileMetadataCache = fileMetadataCache;
        this.policy = policy;
        this.scanPolicy = scanPolicy;
        this.clock = clock;
    }

    public UploadOutcome upload(Actor actor, MultipartFile multipartFile, String description, String idempotencyKey) {
        validate(multipartFile);
        String originalFileName = cleanFileName(multipartFile.getOriginalFilename());
        String normalizedDescription = normalizeDescription(description);
        StoredFile target = fileStorage.allocate(originalFileName);

        UploadRequest request = null;
        if (idempotencyKey != null) {
            String requestHash = requestHash(multipartFile, originalFileName, normalizedDescription);
            Claim claim = claim(actor.userId(), validateKey(idempotencyKey), requestHash, target.storagePath());
            if (claim.replayedFile() != null) {
                return new UploadOutcome(claim.replayedFile(), true);
            }
            request = claim.request();
        }

        writeObject(target, multipartFile, request);

        Instant now = clock.instant();
        FileMetadata metadata = new FileMetadata(
                null,
                actor.userId(),
                originalFileName,
                target.storedFileName(),
                target.storagePath(),
                multipartFile.getSize(),
                detectMimeType(multipartFile),
                scanPolicy.initialStatus(),
                scanPolicy.initialScanStatus(),
                normalizedDescription,
                now,
                now
        );
        FileMetadata saved = recordWithRetry(metadata, request);
        fileMetadataCache.put(saved);
        return new UploadOutcome(saved, false);
    }

    private void writeObject(StoredFile target, MultipartFile multipartFile, UploadRequest request) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            fileStorage.write(target, inputStream, multipartFile.getSize(), detectMimeType(multipartFile));
        } catch (IOException | RuntimeException exception) {
            // 쓰기 응답만 유실되고 객체는 저장됐을 수 있다. 위치를 알고 있으므로 지워 둔다.
            log.error("File upload storage failed. storagePath={}, size={}", target.storagePath(), multipartFile.getSize(), exception);
            deleteObjectQuietly(target.storagePath(), "object write failed");
            markFailedQuietly(request);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private FileMetadata recordWithRetry(FileMetadata metadata, UploadRequest request) {
        Long requestId = request == null ? null : request.id();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= policy.recordAttempts(); attempt++) {
            try {
                return uploadRecorder.record(metadata, requestId);
            } catch (UploadRequestSupersededException exception) {
                log.warn("Upload attempt superseded by a newer attempt. storagePath={}", metadata.storagePath());
                deleteObjectQuietly(metadata.storagePath(), "upload attempt superseded");
                throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("File metadata save failed. attempt={}/{}, storagePath={}",
                        attempt, policy.recordAttempts(), metadata.storagePath(), exception);
                if (attempt < policy.recordAttempts()) {
                    sleep(attempt);
                }
            }
        }
        return resolveUnknownOutcome(metadata, request, lastFailure);
    }

    /** 재시도가 모두 실패했다. 실제로 커밋됐는지 확인한 뒤에만 객체를 지운다. */
    private FileMetadata resolveUnknownOutcome(FileMetadata metadata, UploadRequest request, RuntimeException lastFailure) {
        Optional<FileMetadata> committed;
        try {
            committed = fileMetadataRepository.findByStoragePath(metadata.storagePath());
        } catch (RuntimeException checkFailure) {
            log.error("Upload outcome unknown; object kept for reconciliation. storagePath={}",
                    metadata.storagePath(), checkFailure);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        if (committed.isPresent()) {
            log.warn("File metadata was committed despite save error. storagePath={}", metadata.storagePath());
            return committed.get();
        }
        log.error("File metadata save failed after retries; deleting object. storagePath={}",
                metadata.storagePath(), lastFailure);
        deleteObjectQuietly(metadata.storagePath(), "metadata save failed");
        markFailedQuietly(request);
        throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
    }

    private Claim claim(Long ownerId, String idempotencyKey, String requestHash, String storagePath) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(policy.idempotencyTtl());
        Optional<UploadRequest> existing = uploadRequestRepository.find(ownerId, idempotencyKey);
        if (existing.isEmpty()) {
            try {
                return Claim.started(uploadRequestRepository.insert(
                        UploadRequest.start(ownerId, idempotencyKey, requestHash, storagePath, now, expiresAt)));
            } catch (DuplicateUploadRequestException exception) {
                existing = uploadRequestRepository.find(ownerId, idempotencyKey);
                if (existing.isEmpty()) {
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
                }
            }
        }

        UploadRequest current = existing.get();
        if (current.isExpired(now)) {
            return restart(current, requestHash, storagePath, now, expiresAt);
        }
        if (!current.requestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_MISMATCH);
        }
        return switch (current.status()) {
            case COMPLETED -> Claim.replay(fileMetadataRepository.findById(current.fileId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
            case FAILED -> restart(current, requestHash, storagePath, now, expiresAt);
            case IN_PROGRESS -> {
                if (!current.isStale(now, policy.inProgressTimeout())) {
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
                }
                log.warn("Taking over stale upload request. uploadRequestId={}, previousStoragePath={}",
                        current.id(), current.storagePath());
                yield restart(current, requestHash, storagePath, now, expiresAt);
            }
        };
    }

    private Claim restart(UploadRequest current, String requestHash, String storagePath, Instant now, Instant expiresAt) {
        UploadRequest restarted = current.restarted(requestHash, storagePath, now, expiresAt);
        if (!uploadRequestRepository.restart(current, restarted)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        return Claim.started(restarted);
    }

    private void deleteObjectQuietly(String storagePath, String reason) {
        try {
            fileStorage.delete(storagePath);
            log.info("Compensating object delete completed. reason={}, storagePath={}", reason, storagePath);
        } catch (IOException | RuntimeException exception) {
            log.error("Compensating object delete failed; object left for reconciliation. reason={}, storagePath={}",
                    reason, storagePath, exception);
        }
    }

    private void markFailedQuietly(UploadRequest request) {
        if (request == null) {
            return;
        }
        try {
            uploadRequestRepository.fail(request.id(), request.storagePath(), clock.instant());
        } catch (RuntimeException exception) {
            log.warn("Failed to mark upload request as failed; it will be treated as stale. uploadRequestId={}",
                    request.id(), exception);
        }
    }

    private void sleep(int attempt) {
        long millis = policy.recordBackoff().toMillis() * attempt;
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String validateKey(String idempotencyKey) {
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return idempotencyKey;
    }

    /** 파일명·크기·설명·본문 SHA-256으로 같은 요청인지 판단한다. */
    private String requestHash(MultipartFile multipartFile, String originalFileName, String description) {
        try {
            MessageDigest content = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = new DigestInputStream(multipartFile.getInputStream(), content)) {
                inputStream.transferTo(OutputStream.nullOutputStream());
            }
            MessageDigest request = MessageDigest.getInstance("SHA-256");
            String canonical = originalFileName + "\n" + multipartFile.getSize() + "\n"
                    + (description == null ? "" : description) + "\n" + HexFormat.of().formatHex(content.digest());
            return HexFormat.of().formatHex(request.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void validate(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (multipartFile.getSize() > policy.maxSizeBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String extension = extensionOf(cleanFileName(multipartFile.getOriginalFilename()));
        if (!policy.isAllowedExtension(extension)) {
            throw new BusinessException(ErrorCode.FILE_INVALID_TYPE);
        }
    }

    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return Paths.get(fileName).getFileName().toString();
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String detectMimeType(MultipartFile multipartFile) {
        String contentType = multipartFile.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private record Claim(UploadRequest request, FileMetadata replayedFile) {

        static Claim started(UploadRequest request) {
            return new Claim(request, null);
        }

        static Claim replay(FileMetadata file) {
            return new Claim(null, file);
        }
    }
}
