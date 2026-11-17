package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.repository.FileMetadataRepository;
import com.example.fileshare.file.repository.UploadRequestRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 파일 메타데이터 저장과 업로드 요청 완료 처리를 한 트랜잭션으로 묶는다. */
@Component
public class UploadRecorder {

    private final FileMetadataRepository fileMetadataRepository;
    private final UploadRequestRepository uploadRequestRepository;

    public UploadRecorder(FileMetadataRepository fileMetadataRepository, UploadRequestRepository uploadRequestRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.uploadRequestRepository = uploadRequestRepository;
    }

    @Transactional
    public FileMetadata record(FileMetadata metadata, Long uploadRequestId) {
        FileMetadata saved = fileMetadataRepository.save(metadata);
        if (uploadRequestId != null
                && !uploadRequestRepository.complete(uploadRequestId, metadata.storagePath(), saved.id(), Instant.now())) {
            throw new UploadRequestSupersededException();
        }
        return saved;
    }

    /** 오래 걸린 시도가 늦게 끝났는데 같은 키로 이미 새 시도가 시작된 경우다. 이 시도의 메타데이터는 롤백한다. */
    public static class UploadRequestSupersededException extends RuntimeException {

        UploadRequestSupersededException() {
            super("Upload request was taken over by a newer attempt");
        }
    }
}
