package com.example.fileshare.file.repository;

import com.example.fileshare.file.domain.UploadRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadRequestRepository {

    Optional<UploadRequest> find(Long ownerId, String idempotencyKey);

    /**
     * 새 요청을 기록한다.
     *
     * @throws DuplicateUploadRequestException 같은 사용자·키의 기록이 이미 있을 때
     */
    UploadRequest insert(UploadRequest request);

    /** {@code current}의 version이 그대로일 때만 IN_PROGRESS로 다시 시작한다. 다른 요청이 먼저 바꿨다면 false. */
    boolean restart(UploadRequest current, UploadRequest restarted);

    /** 이 시도의 저장 위치로 진행 중일 때만 완료 처리한다. 오래 걸린 이전 시도가 늦게 완료하는 것을 막는다. */
    boolean complete(Long id, String storagePath, Long fileId, Instant now);

    /** 이 시도의 저장 위치로 진행 중일 때만 실패 처리한다. */
    boolean fail(Long id, String storagePath, Instant now);

    List<UploadRequest> findInProgressUpdatedBefore(Instant threshold);

    int deleteExpired(Instant now);
}
