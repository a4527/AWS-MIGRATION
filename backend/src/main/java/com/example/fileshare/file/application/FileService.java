package com.example.fileshare.file.application;

import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.config.security.AuthUserPrincipal;
import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.example.fileshare.file.repository.FileMetadataRepository;
import com.example.fileshare.infra.storage.FileStorage;
import com.example.fileshare.infra.storage.StorageObject;
import com.example.fileshare.infra.storage.StoredFile;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorage fileStorage;
    private final FileMetadataCache fileMetadataCache;
    private final long maxSizeBytes;
    private final Set<String> allowedExtensions;

    public FileService(
            FileMetadataRepository fileMetadataRepository,
            FileStorage fileStorage,
            FileMetadataCache fileMetadataCache,
            @Value("${app.file.max-size-bytes}") long maxSizeBytes,
            @Value("${app.file.allowed-extensions}") String allowedExtensions
    ) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileStorage = fileStorage;
        this.fileMetadataCache = fileMetadataCache;
        this.maxSizeBytes = maxSizeBytes;
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
                .map(extension -> extension.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toUnmodifiableSet());
    }

    public FileMetadata upload(AuthUserPrincipal principal, MultipartFile multipartFile, String description) {
        validate(multipartFile);

        String originalFileName = cleanFileName(multipartFile.getOriginalFilename());
        try {
            StoredFile storedFile = fileStorage.store(originalFileName, multipartFile.getInputStream());
            Instant now = Instant.now();
            FileMetadata metadata = new FileMetadata(
                    null,
                    principal.userId(),
                    originalFileName,
                    storedFile.storedFileName(),
                    storedFile.storagePath(),
                    multipartFile.getSize(),
                    detectMimeType(multipartFile),
                    FileStatus.AVAILABLE,
                    ScanStatus.CLEAN,
                    normalizeDescription(description),
                    now,
                    now
            );
            FileMetadata saved = fileMetadataRepository.save(metadata);
            fileMetadataCache.put(saved);
            return saved;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public List<FileMetadata> listMine(AuthUserPrincipal principal) {
        return fileMetadataRepository.findVisibleByOwnerId(principal.userId());
    }

    public FileMetadata get(AuthUserPrincipal principal, Long fileId) {
        FileMetadata metadata = findVisible(fileId);
        assertReadable(principal, metadata);
        return metadata;
    }

    public FileDownload download(AuthUserPrincipal principal, Long fileId) {
        FileMetadata metadata = get(principal, fileId);
        if (metadata.status() == FileStatus.QUARANTINED) {
            throw new BusinessException(ErrorCode.FILE_QUARANTINED);
        }
        if (metadata.status() != FileStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        try {
            StorageObject storageObject = fileStorage.load(metadata.storagePath());
            return new FileDownload(metadata, storageObject.resource(), storageObject.contentLength());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    public FileMetadata delete(AuthUserPrincipal principal, Long fileId) {
        FileMetadata metadata = findVisible(fileId);
        assertReadable(principal, metadata);
        metadata.markDeleted();
        FileMetadata saved = fileMetadataRepository.save(metadata);
        fileMetadataCache.evict(fileId);
        return saved;
    }

    private void validate(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (multipartFile.getSize() > maxSizeBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String originalFileName = cleanFileName(multipartFile.getOriginalFilename());
        String extension = extensionOf(originalFileName);
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_INVALID_TYPE);
        }
    }

    private FileMetadata findVisible(Long fileId) {
        FileMetadata metadata = fileMetadataCache.findById(fileId)
                .or(() -> fileMetadataRepository.findById(fileId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        fileMetadataCache.put(metadata);
        if (metadata.status() == FileStatus.DELETED) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return metadata;
    }

    private void assertReadable(AuthUserPrincipal principal, FileMetadata metadata) {
        if (!metadata.ownerId().equals(principal.userId()) && !"ADMIN".equals(principal.role())) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
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
}
