package com.example.fileshare.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.fileshare.file.domain.FileMetadata;
import com.example.fileshare.file.domain.FileStatus;
import com.example.fileshare.file.domain.ScanStatus;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisFileMetadataCacheTests {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ScheduledExecutorService scheduler;
    private SimpleMeterRegistry meters;
    private RedisFileMetadataCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        scheduler = mock(ScheduledExecutorService.class);
        meters = new SimpleMeterRegistry();
        cache = new RedisFileMetadataCache(redis, new ObjectMapper(), meters,
                scheduler, List.of(Duration.ZERO, Duration.ZERO), List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)));
    }

    @Test
    void readFailureFallsBackToDatabase() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.findById(1L)).isEmpty();
    }

    @Test
    void plainWriteFailureIsSwallowed() {
        failWrites();

        cache.put(metadata(FileStatus.AVAILABLE));

        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void latestWriteRetriesImmediatelyBeforeSucceeding() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("blip"))
                .thenReturn(1L);

        cache.putLatest(metadata(FileStatus.DELETED));

        verify(redis, times(2)).execute(any(RedisScript.class), anyList(), any(), any(), any());
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void latestWriteFallsBackToBackgroundRetryAndRecordsPermanentFailure() {
        failWrites();
        List<Runnable> scheduled = captureScheduledTasks();

        cache.putLatest(metadata(FileStatus.DELETED));
        verify(redis, times(3)).execute(any(RedisScript.class), anyList(), any(), any(), any());
        assertThat(scheduled).hasSize(1);

        scheduled.get(0).run();
        assertThat(scheduled).hasSize(2);
        scheduled.get(1).run();

        assertThat(meters.counter("file.cache.update.failures").count()).isEqualTo(1.0);
    }

    @Test
    void backgroundRetrySucceedsWhenRedisRecovers() {
        failWrites();
        List<Runnable> scheduled = captureScheduledTasks();
        cache.putLatest(metadata(FileStatus.DELETED));

        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);
        scheduled.get(0).run();

        assertThat(scheduled).hasSize(1);
        assertThat(meters.counter("file.cache.update.failures").count()).isZero();
    }

    @Test
    void writesSendVersionDerivedFromUpdatedAt() {
        FileMetadata deleted = metadata(FileStatus.DELETED);
        ArgumentCaptor<Object> version = ArgumentCaptor.forClass(Object.class);

        cache.putLatest(deleted);

        verify(redis).execute(eq(RedisFileMetadataCache.PUT_IF_NOT_OLDER), eq(List.of("files:metadata:7")),
                any(), version.capture(), eq(String.valueOf(Duration.ofMinutes(5).toMillis())));
        assertThat(version.getValue()).isEqualTo(String.valueOf(RedisFileMetadataCache.versionOf(deleted.updatedAt())));
        assertThat(RedisFileMetadataCache.versionOf(Instant.parse("2026-11-17T00:00:00.000002Z")))
                .isGreaterThan(RedisFileMetadataCache.versionOf(Instant.parse("2026-11-17T00:00:00.000001Z")));
    }

    private void failWrites() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));
    }

    private List<Runnable> captureScheduledTasks() {
        List<Runnable> tasks = new ArrayList<>();
        when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return null;
        });
        return tasks;
    }

    private FileMetadata metadata(FileStatus status) {
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        return new FileMetadata(7L, 1L, "note.txt", "a.txt", "a.txt", 5, "text/plain", status, ScanStatus.CLEAN,
                null, now, now);
    }
}
