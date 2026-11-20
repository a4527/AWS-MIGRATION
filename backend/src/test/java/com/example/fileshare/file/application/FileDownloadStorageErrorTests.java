package com.example.fileshare.file.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.fileshare.common.actor.Actor;
import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.example.fileshare.file.repository.InMemoryFileMetadataRepository;
import com.example.fileshare.infra.storage.FileStorage;
import com.example.fileshare.infra.storage.StorageObjectNotFoundException;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** OPS-01 T3: 저장소 접근 실패를 "파일 없음"으로 알리지 않는다. */
class FileDownloadStorageErrorTests {

    private final InMemoryFileMetadataRepository files = new InMemoryFileMetadataRepository();
    private final FileStorage storage = mock(FileStorage.class);
    private final FileService service = new FileService(files, storage, new NoOpFileMetadataCache());
    private final Actor owner = new Actor(1L, false);

    private Long availableFile() {
        Instant now = Instant.now();
        return files.save(new FileMetadata(null, 1L, "a.txt", "files/a.txt", "files/a.txt", 1, "text/plain",
                FileStatus.AVAILABLE, ScanStatus.CLEAN, null, now, now)).id();
    }

    @Test
    void accessDeniedOrOutageIsServiceUnavailable() throws Exception {
        Long id = availableFile();
        when(storage.load(anyString())).thenThrow(new IOException("AccessDenied"));
        assertThatThrownBy(() -> service.download(owner, id))
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.FILE_STORAGE_UNAVAILABLE);
    }

    @Test
    void missingObjectIsNotFound() throws Exception {
        Long id = availableFile();
        when(storage.load(anyString())).thenThrow(new StorageObjectNotFoundException("files/a.txt", null));
        assertThatThrownBy(() -> service.download(owner, id))
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
