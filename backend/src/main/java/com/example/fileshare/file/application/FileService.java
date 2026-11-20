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

    public List<FileMetadata> listMine(Actor actor) {
        return fileMetadataRepository.findVisibleByOwnerId(actor.userId());
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
