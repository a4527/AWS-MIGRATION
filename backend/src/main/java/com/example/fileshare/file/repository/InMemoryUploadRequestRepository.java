package com.example.fileshare.file.repository;

import com.example.fileshare.file.domain.UploadRequest;
import com.example.fileshare.file.domain.UploadRequestStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryUploadRequestRepository implements UploadRequestRepository {

    private final Map<String, UploadRequest> requests = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public synchronized Optional<UploadRequest> find(Long ownerId, String idempotencyKey) {
        return Optional.ofNullable(requests.get(key(ownerId, idempotencyKey)));
    }

    @Override
    public synchronized UploadRequest insert(UploadRequest request) {
        String key = key(request.ownerId(), request.idempotencyKey());
        if (requests.containsKey(key)) {
            throw new DuplicateUploadRequestException(null);
        }
        UploadRequest saved = request.withId(sequence.getAndIncrement());
        requests.put(key, saved);
        return saved;
    }

    @Override
    public synchronized boolean restart(UploadRequest current, UploadRequest restarted) {
        String key = key(current.ownerId(), current.idempotencyKey());
        UploadRequest stored = requests.get(key);
        if (stored == null || stored.version() != current.version()) {
            return false;
        }
        requests.put(key, restarted);
        return true;
    }

    @Override
    public synchronized boolean complete(Long id, String storagePath, Long fileId, Instant now) {
        return findInProgress(id, storagePath)
                .map(request -> requests.put(key(request.ownerId(), request.idempotencyKey()), request.completed(fileId, now)) != null)
                .orElse(false);
    }

    @Override
    public synchronized boolean fail(Long id, String storagePath, Instant now) {
        return findInProgress(id, storagePath)
                .map(request -> requests.put(key(request.ownerId(), request.idempotencyKey()), request.failed(now)) != null)
                .orElse(false);
    }

    @Override
    public synchronized List<UploadRequest> findInProgressUpdatedBefore(Instant threshold) {
        return requests.values().stream()
                .filter(request -> request.status() == UploadRequestStatus.IN_PROGRESS)
                .filter(request -> request.updatedAt().isBefore(threshold))
                .toList();
    }

    @Override
    public synchronized int deleteExpired(Instant now) {
        int before = requests.size();
        requests.values().removeIf(request -> request.isExpired(now));
        return before - requests.size();
    }

    private Optional<UploadRequest> findInProgress(Long id, String storagePath) {
        return requests.values().stream()
                .filter(request -> request.id().equals(id))
                .filter(request -> request.status() == UploadRequestStatus.IN_PROGRESS)
                .filter(request -> Objects.equals(request.storagePath(), storagePath))
                .findFirst();
    }

    private String key(Long ownerId, String idempotencyKey) {
        return ownerId + ":" + idempotencyKey;
    }
}
