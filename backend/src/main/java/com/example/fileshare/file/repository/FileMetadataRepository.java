package com.example.fileshare.file.repository;

import com.example.fileshare.file.domain.FileMetadata;
import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository {

    FileMetadata save(FileMetadata fileMetadata);

    Optional<FileMetadata> findById(Long id);

    List<FileMetadata> findVisibleByOwnerId(Long ownerId);

    /** 논리 삭제된 행을 포함해 저장 위치로 메타데이터를 찾는다. 업로드 결과 확인과 정합성 점검에 쓴다. */
    Optional<FileMetadata> findByStoragePath(String storagePath);
}
