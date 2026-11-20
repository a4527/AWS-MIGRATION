package com.example.fileshare.file.repository.jpa;

import com.example.fileshare.file.domain.UploadRequest;
import com.example.fileshare.file.domain.UploadRequestStatus;
import com.example.fileshare.file.repository.DuplicateUploadRequestException;
import com.example.fileshare.file.repository.UploadRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
public class JpaUploadRequestRepositoryAdapter implements UploadRequestRepository {

    private final SpringDataUploadRequestJpaRepository jpaRepository;

    public JpaUploadRequestRepositoryAdapter(SpringDataUploadRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UploadRequest> find(Long ownerId, String idempotencyKey) {
        return jpaRepository.findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey).map(UploadRequestEntity::toDomain);
    }

    @Override
    public UploadRequest insert(UploadRequest request) {
        try {
            return jpaRepository.saveAndFlush(UploadRequestEntity.from(request)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateUploadRequestException(exception);
        }
    }

    @Override
    @Transactional
    public boolean restart(UploadRequest current, UploadRequest restarted) {
        return jpaRepository.restart(current.id(), current.version(), UploadRequestStatus.IN_PROGRESS,
                restarted.requestHash(), restarted.storagePath(), restarted.updatedAt(), restarted.expiresAt()) == 1;
    }

    @Override
    @Transactional
    public boolean complete(Long id, String storagePath, Long fileId, Instant now) {
        return jpaRepository.transition(id, storagePath, UploadRequestStatus.IN_PROGRESS,
                UploadRequestStatus.COMPLETED, fileId, now) == 1;
    }

    @Override
    @Transactional
    public boolean fail(Long id, String storagePath, Instant now) {
        return jpaRepository.transition(id, storagePath, UploadRequestStatus.IN_PROGRESS,
                UploadRequestStatus.FAILED, null, now) == 1;
    }

    @Override
    public List<UploadRequest> findInProgressUpdatedBefore(Instant threshold) {
        return jpaRepository.findByStatusAndUpdatedAtBefore(UploadRequestStatus.IN_PROGRESS, threshold).stream()
                .map(UploadRequestEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return jpaRepository.deleteExpired(now);
    }
}
