package com.example.fileshare.file.application;

import com.example.fileshare.common.actor.Actor;
import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.repository.FileMetadataRepository;
import com.example.fileshare.infra.storage.FileStorage;
import com.example.fileshare.infra.storage.StorageObject;
import java.io.IOException;
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
