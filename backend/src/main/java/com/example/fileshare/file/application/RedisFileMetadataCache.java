package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 메타데이터 캐시다.
 *
 * <ul>
 *   <li>읽기·쓰기 오류는 로그만 남기고 호출자에게 전달하지 않는다. 조회는 DB로 우회한다.</li>
 *   <li>쓰기는 Lua 스크립트로 캐시에 있는 값의 version(updatedAt)과 비교해 더 오래된 값을 거부한다.
 *       삭제 직전 값을 읽은 조회 요청이 삭제 뒤에 캐시를 덮어쓰는 경쟁을 막는다.</li>
 *   <li>삭제처럼 반드시 반영해야 하는 변경은 즉시 재시도 후 백그라운드에서 지수 간격으로 다시 시도한다.</li>
 * </ul>
 */
@Component
@Profile("redis")
public class RedisFileMetadataCache implements FileMetadataCache {

    static final Duration TTL = Duration.ofMinutes(5);
    static final String KEY_PREFIX = "files:metadata:";
    static final List<Duration> IMMEDIATE_RETRY_DELAYS = List.of(Duration.ofMillis(50), Duration.ofMillis(100));
    static final List<Duration> BACKGROUND_RETRY_DELAYS = List.of(
            Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8),
            Duration.ofSeconds(16), Duration.ofSeconds(32), Duration.ofSeconds(64));

    static final RedisScript<Long> PUT_IF_NOT_OLDER = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current then
              local ok, decoded = pcall(cjson.decode, current)
              if ok and type(decoded) == 'table' and decoded['version'] ~= nil
                  and tonumber(decoded['version']) > tonumber(ARGV[2]) then
                return 0
              end
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private static final Logger log = LoggerFactory.getLogger(RedisFileMetadataCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService retryExecutor;
    private final List<Duration> immediateRetryDelays;
    private final List<Duration> backgroundRetryDelays;
    private final Counter permanentFailures;

    @Autowired
    public RedisFileMetadataCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this(redisTemplate, objectMapper, meterRegistry, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "file-cache-retry");
            thread.setDaemon(true);
            return thread;
        }), IMMEDIATE_RETRY_DELAYS, BACKGROUND_RETRY_DELAYS);
    }

    RedisFileMetadataCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            ScheduledExecutorService retryExecutor,
            List<Duration> immediateRetryDelays,
            List<Duration> backgroundRetryDelays
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.retryExecutor = retryExecutor;
        this.immediateRetryDelays = immediateRetryDelays;
        this.backgroundRetryDelays = backgroundRetryDelays;
        this.permanentFailures = Counter.builder("file.cache.update.failures")
                .description("파일 메타데이터 캐시 상태 반영이 백그라운드 재시도까지 모두 실패한 횟수")
                .register(meterRegistry);
    }

    @Override
    public Optional<FileMetadata> findById(Long id) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(key(id));
        } catch (RuntimeException exception) {
            log.warn("Cache read failed; falling back to database. fileId={}", id, exception);
            return Optional.empty();
        }
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, CachedFileMetadata.class).toDomain());
        } catch (Exception exception) {
            log.warn("Cache entry unreadable; ignoring it. fileId={}", id, exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(FileMetadata fileMetadata) {
        if (fileMetadata.id() == null) {
            return;
        }
        try {
            write(fileMetadata);
        } catch (RuntimeException exception) {
            log.warn("Cache write failed; continuing without cache. fileId={}", fileMetadata.id(), exception);
        }
    }

    @Override
    public void putLatest(FileMetadata fileMetadata) {
        if (fileMetadata.id() == null) {
            return;
        }
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= immediateRetryDelays.size(); attempt++) {
            if (attempt > 0) {
                sleep(immediateRetryDelays.get(attempt - 1));
            }
            try {
                write(fileMetadata);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        log.warn("Cache update failed after immediate retries; retrying in background. fileId={}, status={}",
                fileMetadata.id(), fileMetadata.status(), lastFailure);
        scheduleBackgroundRetry(fileMetadata, 0);
    }

    private void scheduleBackgroundRetry(FileMetadata fileMetadata, int index) {
        if (index >= backgroundRetryDelays.size()) {
            permanentFailures.increment();
            log.error("Cache update failed permanently; stale entry may remain until TTL. fileId={}, status={}, ttlSeconds={}",
                    fileMetadata.id(), fileMetadata.status(), TTL.toSeconds());
            return;
        }
        retryExecutor.schedule(() -> {
            try {
                write(fileMetadata);
                log.info("Cache update succeeded in background. fileId={}, attempt={}", fileMetadata.id(), index + 1);
            } catch (RuntimeException exception) {
                log.warn("Background cache update failed. fileId={}, attempt={}", fileMetadata.id(), index + 1, exception);
                scheduleBackgroundRetry(fileMetadata, index + 1);
            }
        }, backgroundRetryDelays.get(index).toMillis(), TimeUnit.MILLISECONDS);
    }

    private void write(FileMetadata fileMetadata) {
        String value;
        try {
            value = objectMapper.writeValueAsString(CachedFileMetadata.from(fileMetadata));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize cache entry", exception);
        }
        redisTemplate.execute(PUT_IF_NOT_OLDER, List.of(key(fileMetadata.id())),
                value, String.valueOf(versionOf(fileMetadata.updatedAt())), String.valueOf(TTL.toMillis()));
    }

    @PreDestroy
    void shutdown() {
        retryExecutor.shutdownNow();
    }

    static long versionOf(Instant updatedAt) {
        return updatedAt.getEpochSecond() * 1_000_000L + updatedAt.getNano() / 1_000;
    }

    private void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String key(Long id) {
        return KEY_PREFIX + id;
    }

    record CachedFileMetadata(
            Long id,
            Long ownerId,
            String originalFileName,
            String storedFileName,
            String storagePath,
            long size,
            String mimeType,
            FileStatus status,
            ScanStatus scanStatus,
            String description,
            Instant deletedAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {

        static CachedFileMetadata from(FileMetadata metadata) {
            return new CachedFileMetadata(
                    metadata.id(),
                    metadata.ownerId(),
                    metadata.originalFileName(),
                    metadata.storedFileName(),
                    metadata.storagePath(),
                    metadata.size(),
                    metadata.mimeType(),
                    metadata.status(),
                    metadata.scanStatus(),
                    metadata.description(),
                    metadata.deletedAt(),
                    metadata.createdAt(),
                    metadata.updatedAt(),
                    versionOf(metadata.updatedAt())
            );
        }

        FileMetadata toDomain() {
            FileMetadata metadata = new FileMetadata(
                    id,
                    ownerId,
                    originalFileName,
                    storedFileName,
                    storagePath,
                    size,
                    mimeType,
                    status,
                    scanStatus,
                    description,
                    createdAt,
                    updatedAt
            );
            if (deletedAt != null) {
                metadata.withDeletedAt(deletedAt);
            }
            return metadata;
        }
    }
}
