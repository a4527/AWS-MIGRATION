package com.example.fileshare.file.repository;

import java.time.Instant;

import com.example.fileshare.file.domain.ScanStatus;

import com.example.fileshare.file.domain.FileStatus;

import com.example.fileshare.file.domain.FileMetadata;
import java.util.List;
import java.util.Optional;

public interface FileMetadataRepository {

    FileMetadata save(FileMetadata fileMetadata);

    Optional<FileMetadata> findById(Long id);

    List<FileMetadata> findVisibleByOwnerId(Long ownerId);

    /** 논리 삭제된 행을 포함해 저장 위치로 메타데이터를 찾는다. 업로드 결과 확인과 정합성 점검에 쓴다. */
    Optional<FileMetadata> findByStoragePath(String storagePath);

    /**
     * 검사 대기(PENDING)이고 삭제되지 않은 행만 검사 결과로 바꾼다(FILE-03). 바뀐 행 수를 돌려준다.
     * 중복·역순 결과나 이미 삭제된 파일에는 0을 돌려주므로 재처리해도 상태가 되돌아가지 않는다.
     */
    int applyScanResult(String storagePath, ScanStatus scanStatus, FileStatus status, Instant at);

    /** {@code before}보다 오래 PENDING인 파일의 저장 위치를 돌려준다(검사 시간 초과 처리용). */
    List<String> findPendingScanBefore(Instant before);
}
