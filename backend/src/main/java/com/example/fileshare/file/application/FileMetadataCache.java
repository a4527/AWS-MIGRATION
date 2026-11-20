package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;
import java.util.Optional;

/**
 * 파일 메타데이터 캐시 포트다. 캐시 장애는 호출자에게 예외로 전달하지 않는다.
 *
 * <p>모든 쓰기는 {@code updatedAt}을 버전으로 비교해, 캐시에 있는 값보다 오래된 값은 무시한다.
 */
public interface FileMetadataCache {

    /** 캐시에 없거나 캐시를 읽지 못하면 비어 있다. */
    Optional<FileMetadata> findById(Long id);

    /** 조회·업로드 결과를 한 번 반영한다. 실패하면 로그만 남긴다. */
    void put(FileMetadata fileMetadata);

    /**
     * 상태 변경(삭제 등)을 반드시 반영한다. 즉시 몇 번 다시 시도하고, 그래도 실패하면 백그라운드에서 다시 시도한다.
     * 끝내 실패하면 운영자용 ERROR 로그와 지표를 남긴다. 호출자에게는 예외를 던지지 않는다.
     */
    void putLatest(FileMetadata fileMetadata);
}
