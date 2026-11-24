package com.example.fileshare.file.application;

import com.example.fileshare.common.actor.Actor;
import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.example.fileshare.file.repository.FileMetadataRepository;
import com.example.fileshare.infra.storage.FileStorage;
import com.example.fileshare.infra.storage.StorageObject;
import com.example.fileshare.infra.storage.StorageObjectNotFoundException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 파일 조회·다운로드·삭제를 처리한다. 업로드는 {@link FileUploadService}가 맡는다.
 *
 * <p>캐시는 메타데이터 단건 조회에만 쓴다. 다운로드와 삭제는 상태를 DB에서 다시 읽어 판단한다.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorage fileStorage;
    private final FileMetadataCache fileMetadataCache;

    public FileService(
            FileMetadataRepository fileMetadataRepository,
            FileStorage fileStorage,
            FileMetadataCache fileMetadataCache
    ) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileStorage = fileStorage;
        this.fileMetadataCache = fileMetadataCache;
    }

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 본인 파일을 최신순으로 한 페이지 돌려준다(PERF-02). {@code cursor}는 이전 응답의 {@code nextCursor}다.
     * {@code size}가 1~{@value #MAX_PAGE_SIZE} 밖이거나 cursor가 양의 정수가 아니면 VALIDATION_ERROR.
     */
    public FilePage listMine(Actor actor, String cursor, String size) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : parsePositive(size, MAX_PAGE_SIZE);
        Long beforeId = cursor == null ? null : parsePositive(cursor, Long.MAX_VALUE);
        // 한 개 더 읽어 다음 페이지가 있는지 판단한다(count 쿼리 없음).
        List<FileMetadata> rows = fileMetadataRepository.findVisibleByOwnerId(actor.userId(), beforeId, pageSize + 1);
        if (rows.size() <= pageSize) {
            return new FilePage(rows, null);
        }
        List<FileMetadata> items = rows.subList(0, pageSize);
        return new FilePage(List.copyOf(items), items.get(pageSize - 1).id());
    }

    private static int parsePositive(String raw, int max) {
        return (int) parsePositive(raw, (long) max);
    }

    private static long parsePositive(String raw, long max) {
        try {
            long value = Long.parseLong(raw);
            if (value >= 1 && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 거절한다.
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    public FileMetadata get(Actor actor, Long fileId) {
        FileMetadata metadata = findVisible(fileId, true);
        assertReadable(actor, metadata);
        return metadata;
    }

    public FileDownload download(Actor actor, Long fileId) {
        FileMetadata metadata = findVisible(fileId, false);
        assertReadable(actor, metadata);
        if (metadata.status() == FileStatus.QUARANTINED) {
            throw new BusinessException(ErrorCode.FILE_QUARANTINED);
        }
        // FILE-03: 검사가 끝나기 전에는 내려주지 않는다(fail closed).
        if (metadata.status() == FileStatus.SCANNING || metadata.scanStatus() == ScanStatus.PENDING) {
            throw new BusinessException(ErrorCode.FILE_SCAN_PENDING);
        }
        if (metadata.status() != FileStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        try {
            StorageObject storageObject = fileStorage.load(metadata.storagePath());
            return new FileDownload(metadata, storageObject.resource(), storageObject.contentLength());
        } catch (StorageObjectNotFoundException exception) {
            // DB에는 있는데 객체가 없다: 정합성 문제다(관리자 고아 점검 대상의 반대 경우).
            log.error("Stored object missing for an existing file. fileId={}, storagePath={}", fileId, metadata.storagePath(), exception);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (IOException exception) {
            // 접근 거부·저장소 장애. "파일 없음"으로 알리면 사용자와 운영자 모두 원인을 잘못 판단한다(OPS-01 T3).
            log.error("File storage unavailable on download. fileId={}, storagePath={}", fileId, metadata.storagePath(), exception);
            throw new BusinessException(ErrorCode.FILE_STORAGE_UNAVAILABLE);
        }
    }

    public FileMetadata delete(Actor actor, Long fileId) {
        FileMetadata metadata = findVisible(fileId, false);
        assertReadable(actor, metadata);
        metadata.markDeleted();
        FileMetadata saved = fileMetadataRepository.save(metadata);
        // 삭제 상태를 캐시에 덮어써 다른 요청이 이전 값을 다시 넣지 못하게 한다. 실패해도 삭제는 성공으로 응답한다.
        fileMetadataCache.putLatest(saved);
        return saved;
    }

    private FileMetadata findVisible(Long fileId, boolean useCache) {
        Optional<FileMetadata> cached = useCache ? fileMetadataCache.findById(fileId) : Optional.empty();
        FileMetadata metadata = cached.orElseGet(() -> {
            FileMetadata loaded = fileMetadataRepository.findById(fileId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            fileMetadataCache.put(loaded);
            return loaded;
        });
        if (metadata.status() == FileStatus.DELETED) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return metadata;
    }

    private void assertReadable(Actor actor, FileMetadata metadata) {
        if (!metadata.ownerId().equals(actor.userId()) && !actor.admin()) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED);
        }
    }
}
