package com.example.fileshare.file.application;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
public class RedisFileMetadataCache implements FileMetadataCache {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "files:metadata:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisFileMetadataCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<FileMetadata> findById(Long id) {
        String value = redisTemplate.opsForValue().get(key(id));
        if (value == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, CachedFileMetadata.class).toDomain());
        } catch (Exception exception) {
            evict(id);
            return Optional.empty();
        }
    }

    @Override
    public void put(FileMetadata fileMetadata) {
        if (fileMetadata.id() == null) {
            return;
        }

        try {
            String value = objectMapper.writeValueAsString(CachedFileMetadata.from(fileMetadata));
            redisTemplate.opsForValue().set(key(fileMetadata.id()), value, TTL);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void evict(Long id) {
        redisTemplate.delete(key(id));
    }

    private String key(Long id) {
        return KEY_PREFIX + id;
    }

    private record CachedFileMetadata(
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
            Instant updatedAt
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
                    metadata.updatedAt()
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
